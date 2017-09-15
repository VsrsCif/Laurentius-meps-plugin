/*
 * Copyright 2015, Supreme Court Republic of Slovenia
 * 
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work except in
 * compliance with the Licence. You may obtain a copy of the Licence at:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence
 * is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the Licence for the specific language governing permissions and limitations under
 * the Licence.
 */
package si.jrc.msh.plugin.meps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.xml.bind.JAXBException;
import si.jrc.msh.plugin.meps.enums.MEPSAction;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.jrc.msh.plugin.meps.enums.MEPSService;
import si.jrc.msh.plugin.meps.exception.MEPSException;
import si.jrc.msh.plugin.meps.utils.MEPSUtils;
import si.jrc.msh.plugin.meps.utils.PackageNumberGenerator;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.email.EmailAttachmentData;
import si.laurentius.commons.email.EmailData;
import si.laurentius.commons.email.EmailUtils;
import si.laurentius.commons.enums.MimeValue;
import si.laurentius.commons.enums.SEDInboxMailStatus;
import si.laurentius.commons.enums.SEDMailPartSource;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.SEDDaoInterface;

import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.StringFormater;
import si.laurentius.commons.utils.Utils;
import si.laurentius.commons.utils.xml.XMLUtils;
import si.laurentius.meps.envelope.EnvelopeData;
import si.laurentius.meps.envelope.PostalData;
import si.laurentius.meps.envelope.PrintContent;
import si.laurentius.msh.inbox.mail.MSHInMail;
import si.laurentius.msh.inbox.payload.MSHInPart;
import si.laurentius.plugin.crontask.CronTaskDef;
import si.laurentius.plugin.crontask.CronTaskPropertyDef;
import si.laurentius.plugin.interfaces.PropertyType;
import si.laurentius.plugin.interfaces.TaskExecutionInterface;
import si.laurentius.plugin.interfaces.exception.TaskException;

/**
 * This is samople of cron task plugin component.
 *
 * @author Jože Rihtaršič
 */
@Stateless
@Local(TaskExecutionInterface.class)
public class MEPSTask implements TaskExecutionInterface {

  public static final String KEY_FOLDER = "meps.folder";
  public static final String KEY_METADATA_FILENAME = "meps.metadata.filename";
  public static final String KEY_METADATA_ENCODING = "meps.metadata.encoding";
  public static final String KEY_SENDER_SEDBOX = "meps.sender.sedbox";
  public static final String KEY_SENDER_SERVICE = "meps.service";
  public static final String KEY_MAX_MAIL_COUT = "meps.mail.max.count";

  public static final String KEY_GENERATE_TEST_FILE = "meps.generate.test.data";

  public static final String KEY_EMAIL_SUBJECT = "meps.email.subject";
  public static final String KEY_EMAIL_FROM = "meps.email.from";
  public static final String KEY_EMAIL_TO = "meps.email.to";

  public static final String[] TEST_STATUS = {"PROCESSED", "DELETED", "IGNORED"};

  /**
   *
   */
  public static final String KEY_MAIL_CONFIG_JNDI = "mail.config.jndi";

  private static final SEDLogger LOG = new SEDLogger(MEPSTask.class);
  public static final String DATE_FORMAT = "%04d%02d%02d";
  public static final String DATE_FORMAT_SVN = "%02d.%02d.%04d";
  public static final String DATETIME_FORMAT = "%04d%02d%02d_%02d02d";

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mDB;

  private MEPSUtils mmuUtils = new MEPSUtils();

  /**
   * execute metod
   *
   * @param p - parameters defined at configuration of task instance
   * @return result description
   */
  @Override
  public String executeTask(Properties p)
          throws TaskException {

    long l = LOG.logStart();
    StringWriter sw = new StringWriter();
    sw.append("Start MEPS export plugin task: \n");

    // ---------------------------
    // read properties
    String sedBox = "";
    if (!p.containsKey(KEY_SENDER_SEDBOX)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Missing parameter:  '" + KEY_SENDER_SEDBOX + "'!");
    } else {
      sedBox = p.getProperty(KEY_SENDER_SEDBOX);
    }

    String[] services;
    if (!p.containsKey(KEY_SENDER_SERVICE)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Missing parameter:  '" + KEY_SENDER_SERVICE + "'!");
    } else {
      services = p.getProperty(KEY_SENDER_SERVICE).split(",");
    }

    String exportFolderMask = "";
    if (!p.containsKey(KEY_FOLDER)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Missing parameter:  '" + KEY_FOLDER + "'!");
    } else {
      exportFolderMask = p.getProperty(KEY_FOLDER);
    }

    String envelopeDataFilenameMask = "";
    if (!p.containsKey(KEY_METADATA_FILENAME)) {
      envelopeDataFilenameMask = "envelopedata_${Number}.txt";
    } else {
      envelopeDataFilenameMask = p.getProperty(KEY_METADATA_FILENAME);
    }

    Charset charset = null;
    if (p.containsKey(KEY_METADATA_ENCODING)) {
      String chs = p.getProperty(KEY_METADATA_ENCODING);
      if (Charset.isSupported(chs)) {
        String msg = "Charset:  '" + chs + "' is not supported!";
        LOG.logError(msg, null);
        throw new TaskException(TaskException.TaskExceptionCode.InitException,
                msg);
      }
      charset = Charset.forName(chs);
    }

    int maxMailProc = 1500;
    if (p.containsKey(KEY_MAX_MAIL_COUT)) {
      String val = p.getProperty(KEY_MAX_MAIL_COUT);
      if (!Utils.isEmptyString(val)) {
        try {
          maxMailProc = Integer.parseInt(val);
        } catch (NumberFormatException nfe) {
          LOG.logError(String.format(
                  "Error parameter '%s'. Value '%s' is not a number Mail count 100 is setted!",
                  KEY_MAX_MAIL_COUT, val), nfe);
        }
      }
    }

    boolean genTestData = false;
    if (p.containsKey(KEY_GENERATE_TEST_FILE)) {
      genTestData = p.getProperty(KEY_GENERATE_TEST_FILE).trim().
              equalsIgnoreCase("true");
    }

    String emailTo = null;
    String emailFrom = null;
    String emailSubject = null;
    if (p.containsKey(KEY_EMAIL_TO)) {
      emailTo = p.getProperty(KEY_EMAIL_TO);
    }
    if (p.containsKey(KEY_EMAIL_FROM)) {
      emailFrom = p.getProperty(KEY_EMAIL_FROM);
    }
    if (p.containsKey(KEY_EMAIL_SUBJECT)) {
      emailSubject = p.getProperty(KEY_EMAIL_SUBJECT);
    }
    String smtpConf = null;
    if (p.containsKey(KEY_MAIL_CONFIG_JNDI)) {
      smtpConf = p.getProperty(KEY_MAIL_CONFIG_JNDI);
    }

    MSHInMail miFilter = new MSHInMail();
    String ebox = sedBox;
    miFilter.setStatus(SEDInboxMailStatus.RECEIVED.getValue());

    miFilter.setAction(MEPSAction.ADD_MAIL.getValue());
    miFilter.setSenderEBox(ebox);

    for (String service : services) {

      miFilter.setService(service);

      File envelopeDataFile = exportMailForFilter(miFilter, sw, sedBox,
              maxMailProc, exportFolderMask, envelopeDataFilenameMask, charset);

      // generate test file
      if (genTestData && envelopeDataFile != null) {
        File testProcFile = new File(envelopeDataFile.getParentFile(),
                envelopeDataFile.getName() + ".processed");
        try (BufferedReader br = new BufferedReader(new FileReader(
                envelopeDataFile));
                FileWriter fileWriter = new FileWriter(testProcFile)) {

          Calendar cal = Calendar.getInstance();
          String date = String.format(DATE_FORMAT_SVN, cal.get(
                  Calendar.DAY_OF_MONTH),
                  cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
          Random r = new Random();

          for (String line; (line = br.readLine()) != null;) {
            String[] ln = line.split("|");

            int masa = r.nextInt(1000) + 11;

            int iStatus = r.nextInt(10);
            iStatus = iStatus > 2 ? 0 : iStatus;

            fileWriter.append(line);

            fileWriter.append(String.format("|x|%d|9|%d|OK|RECEIVED|%s|%s\n",
                    masa, r.nextInt(99999), TEST_STATUS[iStatus], date));

          }
          fileWriter.flush();
        } catch (IOException ex) {
          LOG.logError("Error occured while generating test file.", ex);

        }
      }

      // email
      if (envelopeDataFile != null
              && !Utils.isEmptyString(smtpConf)
              && !Utils.isEmptyString(emailFrom)
              && !Utils.isEmptyString(emailTo)) {
        submitMail(smtpConf, emailSubject, emailFrom, emailTo,
                envelopeDataFile);
      }

      LOG.formatedDebug("Get mail to export for service: %s, receiver %s",
              service, ebox);

    }

    sw.append("End meps plugin task.");
    return sw.toString();
  }

  public File exportMailForFilter(MSHInMail miFilter, StringWriter sw,
          String sedBox, int maxMailProc,
          String exportFolderMask, String envelopeDataFilenameMask, Charset charset) throws TaskException {
    long l = LOG.logStart();
    List<MSHInMail> lst = mDB.
            getDataList(MSHInMail.class, -1, maxMailProc, "Id", "ASC",
                    miFilter);
    sw.append("got " + lst.size() + " mails for sedbox: '" + sedBox + "'!");

    int iPackageNumber = -1;
    File envelopeDataFile = null;
    File exportFolder = null;

    if (lst.isEmpty()) {
      sw.append("No mail for sedbox: '" + sedBox + "'and service: '" + miFilter.
              getService() + "'!");
    } else {

      sw.append(
              "got " + lst.size() + " mail for sedbox: '" + sedBox + "'and service: '" + miFilter.
              getService() + "'!");

      // generate package number      
      try {
        iPackageNumber = PackageNumberGenerator.getInstance().getNextValue();
      } catch (MEPSException ex) {
        String msg = String.format(
                "Error occured while generating task number: %s", ex.
                        getMessage());
        LOG.logError(msg, ex);
        throw new TaskException(TaskException.TaskExceptionCode.InitException,
                msg);
      }
      // create data object for generating folder and envelope data filename;
      Calendar cal = Calendar.getInstance();
      MepsFolderProperties mp = new MepsFolderProperties();
      mp.setService(miFilter.getService());
      mp.setNumber(iPackageNumber + "");

      mp.setDate(String.format(DATE_FORMAT, cal.get(Calendar.YEAR),
              cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));

      mp.setDateTime(String.format(DATETIME_FORMAT, cal.get(Calendar.YEAR),
              cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
              cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));

      // create export folder
      exportFolder = new File(StringFormater.format(exportFolderMask, mp));
      mmuUtils.createFolderIfNotExist(exportFolder);

      // create envelope data
      envelopeDataFile = new File(exportFolder, StringFormater.format(
              envelopeDataFilenameMask, mp));

      // lock all mail from selected list
      String statusMsg = String.format("Start export mail to package: %d",
              iPackageNumber);
      lst.stream().forEach((m) -> {
        try {

          mDB.setStatusToInMail(m, SEDInboxMailStatus.PLOCKED, statusMsg,
                  null, AppConstant.PLUGIN_TYPE);
        } catch (StorageException ex) {
          String msg = String.format(
                  "Error occurred while setting mail: '%s' to PLOCKED status. Err: %s.",
                  m.getId(),
                  ex.getMessage());
          LOG.logError(l, msg, ex);
          sw.append(msg);
        }
      });

      // export all mail
      
      Writer mailDataWriter = null;
      try (FileOutputStream fos =  new FileOutputStream(envelopeDataFile)) {
        mailDataWriter = new  OutputStreamWriter( fos,  charset);
        for (MSHInMail m : lst) {
          exportData(m, mailDataWriter, exportFolder, miFilter.getService(),
                  iPackageNumber);
        }
        mailDataWriter.flush();

      } catch (IOException ex) {
        String msg = String.format(
                "Error occured while exporting to folder: %s. Error: %s",
                exportFolder.getAbsolutePath(), ex.getMessage());
        LOG.logError(msg, ex);
        sw.append(msg);

      } finally {
        if (mailDataWriter != null) {
          try {
            mailDataWriter.close();
          } catch (IOException ex) {
            LOG.formatedWarning(
                    "Warning occured while closing writer for envelope data: %s. Warning: ",
                    envelopeDataFile.getName(), ex.getMessage());
          }
        }
      }

      String statusEndMsg = String.format(
              "Mail is succesfully exported for printing to package: %d",
              iPackageNumber);

      lst.stream().forEach((m) -> {
        try {
          mDB.setStatusToInMail(m, SEDInboxMailStatus.PREADY,
                  statusEndMsg, null, AppConstant.PLUGIN_TYPE);
        } catch (StorageException ex) {
          String msg = String.format(
                  "Error occurred while setting mail to PREADY state: '%s'. Err: %s.",
                  m.getId(),
                  ex.getMessage());
          LOG.logError(l, msg, ex);
          sw.append(msg);
        }
      });

    }
    return envelopeDataFile;

  }

  public String submitMail(String smtpConf, String emailSubject,
          String emailFrom, String emailTo, File metadata) throws TaskException {
    long l = LOG.logStart();
    EmailUtils memailUtil = new EmailUtils();
    StringWriter sw = new StringWriter();
    sw.append("Start report task: ");

    EmailData ed = new EmailData(emailTo, null, emailSubject, null);
    ed.setEmailSenderAddress(emailFrom);
    String strBody = "Export report";
    if (strBody != null) {

      ed.setBody(strBody);
      EmailAttachmentData emd = new EmailAttachmentData("Mail report",
              MimeValue.MIME_TEXT.getMimeType(), metadata);
      ed.getAttachments().add(emd);

      try {
        sw.append("Submit mail\n");
        memailUtil.sendMailMessage(ed, smtpConf);
      } catch (MessagingException | NamingException | IOException ex) {
        LOG.logError(l, "Error submitting report", ex);
        throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
                "Error submitting report: " + ex.getMessage(), ex);
      }
    } else {
      sw.append("Mail not submitted - nothing to submit\n");
    }
    return sw.toString();
  }

  private void exportData(MSHInMail mInMail, Writer metadata,
          File outFolder, String strFormatedTime, int packageId) throws TaskException {
    File envData = null;
    File export = null;
    int pageCount = 0;
    for (MSHInPart mp : mInMail.getMSHInPayload().getMSHInParts()) {
      // ignore header
      if (Objects.equals(mp.getSource(), SEDMailPartSource.EBMS.getValue())) {
        continue;
      }

      if (Objects.equals(MimeValue.MIME_XML.getMimeType(), mp.getMimeType())
              || Objects.equals(MimeValue.MIME_XML1.getMimeType(), mp.
                      getMimeType())) {
        if (envData != null) {
          throw new TaskException(
                  TaskException.TaskExceptionCode.ProcessException,
                  "Mail must have only one XML  attachmetns (Envelope data)!"
          );
        } else {
          envData = StorageUtils.getFile(mp.getFilepath());
        }

      } else if (Objects.equals(MimeValue.MIME_PDF.getMimeType(), mp.
              getMimeType())
              && Objects.equals(SEDMailPartSource.MAIL.getValue(), mp.
                      getSource())
              && Objects.equals(MEPSPayloadPart.CONCATENATED_CONTENT.getType(),
                      mp.getType())) {
        if (export != null) {
          throw new TaskException(
                  TaskException.TaskExceptionCode.ProcessException,
                  "Mail must have only one MEPS attachmetns (concenated.pdf)!"
          );
        } else {
          export = StorageUtils.getFile(mp.getFilepath());
        }
      }
    }

    if (envData == null) {
      throw new TaskException(
              TaskException.TaskExceptionCode.ProcessException,
              "Mail must have one XML  attachmetns (Envelope data)!"
      );
    }

    if (export == null) {
      throw new TaskException(
              TaskException.TaskExceptionCode.ProcessException,
              "Mail must have one PDF attachmetns (Concatenated pdf)!"
      );
    }

    try {
      String conntentFileName = "doc_" + mInMail.getMessageId().replace('@', '_') + ".pdf";
      EnvelopeData ed = (EnvelopeData) XMLUtils.deserialize(envData,
              EnvelopeData.class);

      for (PrintContent pc : ed.getPrintContents()) {
        pageCount += pc.getPageCount().intValue();
      }

      String dataLine = generateDataLine(mInMail.getMessageId(), ed,
              strFormatedTime, conntentFileName, pageCount, packageId);
      metadata.append(dataLine);
      StorageUtils.copyFile(export, new File(outFolder, conntentFileName), true);

    } catch (JAXBException | IOException | StorageException ex) {
      LOG.logError("Error occured while exporting package:" + packageId, ex);
    }

  }

  private String generateDataLine(String mailId, EnvelopeData doe,
          String strFormatedTime, String filename, int pageCount, int packageId) {

    // ger r number
    String strRRNumber = null;
    String strRNumb = "";
    PostalData.UPNCode upn = doe.getPostalData() != null ? doe.getPostalData().
            getUPNCode() : null;

    if (upn != null && upn.getNumber() != null) {
      strRNumb = upn.getNumber().toString();

      while (strRNumb.length() < 8) {
        strRNumb = "0" + strRNumb;
      }
      strRRNumber = upn.getPrefix() + strRNumb + upn.getControl().toString() + upn.
              getSuffix();
    }

    StringWriter out = new StringWriter();
    out.write(toCSVString(filename));          // 1. Ime pdf datoteke 
    out.write(PackageConstants.S_SEPARATOR_DATA); // 
    out.write(strRNumb);                      //  2. R številka
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderMailData().getCaseNumber())); //3. Opravilna številka
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(strFormatedTime));    // 4. Datum pošiljanja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getReceiverAddress().getName())); // 5. Ime in priimek (Naziv) prejemnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getReceiverAddress().getName2())); // 6. Dodatni naziv prejemnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getReceiverAddress().getAddress())); // 7. Naslov bivališča prejemnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getReceiverAddress().getPostalCode())); // 8. Poštna št. prejemnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getReceiverAddress().getTown())); // 9. Kraj bivališča prejemnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderAddress().getName()));  // 10. Naziv pošiljatelja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderAddress().getName2())); // 11. Dodatni naziv pošiljatelja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderAddress().getAddress())); // 12. Naslov pošiljatelja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderAddress().getPostalCode())); // 13. Poštna številka pošiljatelja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderAddress().getTown())); // 14. Kraj pošiljatelja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(mailId); //  15. Kadrovska številka (Se ne uporablja) N!!!! EBMS ID pošiljke
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderMailData().getContentDescription())); // 16. Vsebina
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write("");  // 17. Datum koledarja.
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getPostalData().getSubmitPostalCode())); // 18. Oddajna oznaka pošte
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(upn != null && upn.getControl() != null ? upn.getControl().
            toString() : "");  // 19. Kontrolka
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getSenderMailData().getCaseType())); // 20. Kratka oznaka vpisnika
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(toCSVString(doe.getPostalData().getSubmitPostalName()));// 21. Pošta pošiljanja
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(doe.getPostalData().getSubmitPostalCode()); // 22. Pošta koda
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(strRRNumber != null ? strRRNumber : ""); // 23. Celotna R številka
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(upn != null ? upn.getPrefix() : ""); // 24. Tip R – številke
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write("" + pageCount);  // 25. Število strani
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write("#"); // enc number        
    out.write(doe.getSenderMailData().getMailId());// 26. CID pošiljke
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write("" + packageId); // 27. ID paketa
    out.write(PackageConstants.S_SEPARATOR_DATA);
    out.write(doe.getPostalData().getEnvelopeType()); // 28. Tip vročitve
    out.write(PackageConstants.S_SEPARATOR_LINE);
    return out.toString();
  }

  private String toCSVString(String strVal) {
    String strWriteVal = strVal != null ? strVal : "";
    strWriteVal = strWriteVal.replaceAll("\\|", "¤"); // CRO request
    return strWriteVal;
  }

  /**
   * Retrun cron task definition: name, unique type, description, parameters..
   *
   * @return Cron task definition
   */
  @Override
  public CronTaskDef getDefinition() {
    CronTaskDef tt = new CronTaskDef();
    tt.setType("MEPS-Process");
    tt.setName("MEPS Batch process");
    tt.setDescription("Prepare mail to process");

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_FOLDER, "Export folder", true,
                    PropertyType.String.getType(), null, null,
                    "${laurentius.home}/meps/export/${Date}_${Number}_${Service}"));

    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_METADATA_FILENAME,
            "Metadata filename", true,
            PropertyType.String.getType(), null, null,
            "envelopedata_${Number}.txt"));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_METADATA_ENCODING,
                    "Metadata encoding. If value is null, platform's default charset is setted",
                    false,
                    PropertyType.String.getType(), null, null,
                    "utf-8"));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_SENDER_SEDBOX, "Sender box", true,
                    PropertyType.String.getType(), null,
                    null, null));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_SENDER_SERVICE, "Service", true,
                    PropertyType.MultiList.getType(), null, String.join(
                    ",", MEPSService.serviceNames()), null));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_MAX_MAIL_COUT, "Max mail count", true,
                    PropertyType.Integer.getType(), null, null, "15000"));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_EMAIL_TO,
                    "Receiver email addresses, separated by comma.", false,
                    PropertyType.String.
                            getType(), null, null,
                    "receiver.one@not.exists.com,receiver.two@not.exists.com"));
    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_EMAIL_FROM,
            "Sender email address", false,
            PropertyType.String.
                    getType(), null, null, "change.me@not.exists.com"));
    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_EMAIL_SUBJECT,
            "EMail subject.", false,
            PropertyType.String.
                    getType(), null, null, "[Laurentius] test mail"));
    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_MAIL_CONFIG_JNDI,
                    "Mail config jndi (def: java:jboss/mail/Default)", false,
                    PropertyType.String.
                            getType(), null, null, "java:jboss/mail/Default"));

    tt.getCronTaskPropertyDeves().add(
            createTTProperty(KEY_GENERATE_TEST_FILE,
                    "Generate test data for status report (def: false)", false,
                    PropertyType.Boolean.
                            getType(), null, null, "false"));

    return tt;
  }

  private CronTaskPropertyDef createTTProperty(String key, String desc,
          boolean mandatory,
          String type, String valFormat, String valList, String defValue) {
    CronTaskPropertyDef ttp = new CronTaskPropertyDef();
    ttp.setKey(key);
    ttp.setDescription(desc);
    ttp.setMandatory(mandatory);
    ttp.setType(type);
    ttp.setValueFormat(valFormat);
    ttp.setValueList(valList);
    ttp.setDefValue(defValue);
    return ttp;
  }

}
