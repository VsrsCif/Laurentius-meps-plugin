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
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBException;
import si.jrc.msh.plugin.meps.enums.MEPSAction;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.jrc.msh.plugin.meps.utils.MEPSUtils;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.enums.SEDInboxMailStatus;
import si.laurentius.commons.enums.SEDOutboxMailStatus;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.PModeInterface;
import si.laurentius.commons.interfaces.SEDDaoInterface;
import si.laurentius.commons.interfaces.SEDLookupsInterface;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.StringFormater;
import si.laurentius.commons.utils.Utils;
import si.laurentius.meps.report.MailStatusReport;
import si.laurentius.meps.report.Status;
import si.laurentius.msh.inbox.mail.MSHInMail;
import si.laurentius.msh.outbox.mail.MSHOutMail;
import si.laurentius.msh.outbox.payload.MSHOutPart;
import si.laurentius.msh.outbox.payload.MSHOutPayload;
import si.laurentius.plugin.crontask.CronTaskDef;
import si.laurentius.plugin.crontask.CronTaskPropertyDef;
import si.laurentius.plugin.interfaces.PropertyType;
import si.laurentius.plugin.interfaces.TaskExecutionInterface;
import si.laurentius.plugin.interfaces.exception.TaskException;

/**
 *
 * @author Jože Rihtaršič
 */
@Stateless
@Local(TaskExecutionInterface.class)
public class MEPSStatusSubmitter implements TaskExecutionInterface {

  /**
   *
   */
  public static final String KEY_EXPORT_FOLDER = "file.submit.folder";
  public static final String KEY_FILE_SUFFIX = "file.submit.suffix";
  private static final SEDLogger LOG = new SEDLogger(MEPSStatusSubmitter.class);
  private final SimpleDateFormat mSDF = new SimpleDateFormat("dd.MM.yyyy");

  private static final String FOLDER_SENDING = "sending";
  private static final String FOLDER_SENT = "sent";
  private static final String FOLDER_ERROR = "error";

  private StorageUtils mSU = new StorageUtils();
  private MEPSUtils mmuUtils = new MEPSUtils();

  @EJB(mappedName = SEDJNDI.JNDI_SEDLOOKUPS)
  SEDLookupsInterface mLookups;

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mdao;

  @EJB(mappedName = SEDJNDI.JNDI_PMODE)
  protected PModeInterface mpModeManager;

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

  /**
   *
   * @param p
   * @return
   */
  @Override
  public String executeTask(Properties p)
          throws TaskException {
    long l = LOG.logStart();
    StringWriter sw = new StringWriter();
    int iVal = 0;
    String sfolder;
    String statusSuffix = null;
    if (!p.containsKey(KEY_EXPORT_FOLDER)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Missing parameter:  '" + KEY_EXPORT_FOLDER + "'!");
    } else {
      sfolder = p.getProperty(KEY_EXPORT_FOLDER);
    }
    if (Utils.isEmptyString(sfolder)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Folder parameter must no be null!:  '" + KEY_EXPORT_FOLDER + "'!");
    }
    File rootFolder = new File(StringFormater.replaceProperties(sfolder));
    File sendingFolder = new File(rootFolder, FOLDER_SENDING);
    File sentFolder = new File(rootFolder, FOLDER_SENT);
    File errorFolder = new File(rootFolder, FOLDER_ERROR);

    mmuUtils.createFolderIfNotExist(rootFolder);
    mmuUtils.createFolderIfNotExist(sendingFolder);
    mmuUtils.createFolderIfNotExist(sentFolder);
    mmuUtils.createFolderIfNotExist(errorFolder);

    if (p.containsKey(KEY_FILE_SUFFIX)) {
      statusSuffix = p.getProperty(KEY_FILE_SUFFIX);
    }
    if (Utils.isEmptyString(statusSuffix)) {
      statusSuffix = ".processed";
    } else if (!statusSuffix.startsWith(".")) {
      statusSuffix = "." + statusSuffix;
      statusSuffix = statusSuffix.toLowerCase();
    }

    final String stSuff = statusSuffix;
    File[] flst
            = rootFolder.listFiles((File dir, String name)
                    -> name.endsWith(stSuff) || name.endsWith(stSuff.
            toUpperCase()));

    for (File file : flst) {
      // test if file still exists
      if (!file.exists()) {
        continue;
      }
      // submit data file:
      //- move file to proccess folder
      // - generate status report
      // - generate message
      // - submit status report
      // - move sfile to precessed
      // - if error move to error message

      Path sendingFile = moveFileToFolder(file.toPath(), sendingFolder);
      /*Path sendingFile = null;
      try {

        sendingFile = sendingFolder.toPath().resolve(file.getName());
        Files.move(file.toPath(), sendingFile);
      } catch (IOException ex) {
        String msg = "Error occured while move data to sending folder  '" + file.
                getName() + "'!";
        LOG.logError(msg, ex);
        throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
                msg);
      }*/

      MSHInMail mRef = null;
      List<Status> lstStatuses = new ArrayList<>();
      List<MSHInMail> mailLst = new ArrayList<>();
      try (BufferedReader br = new BufferedReader(new FileReader(sendingFile.
              toFile()))) {
        for (String line; (line = br.readLine()) != null;) {
          Status s = createStatus(line);

          if (s.getStatus().equalsIgnoreCase("IGNORE")) {
            LOG.formatedError(
                    "Ignore Message with embsId %s!",
                    s.getRefToMessageId());
            continue;
          }
          // all mail must have same sender, receiver and serviceType
          List<MSHInMail> mLst = mdao.getMailByMessageId(MSHInMail.class, s.
                  getRefToMessageId());
          if (mLst.size() != 1) {
            LOG.formatedError(
                    "In Message with embsId %s not exist or more than one message",
                    s.getRefToMessageId());
            continue;
          }
          MSHInMail mo = mLst.get(0);
          if (mRef == null) {
            mRef = mo;
            mailLst.add(mo);

            // mo.set status je delivered
            // mail exists
            lstStatuses.add(s);

          } else if (mRef.getService().equals(mo.getService())
                  && mRef.getSenderEBox().equals(mo.getSenderEBox())
                  && mRef.getReceiverEBox().equals(mo.getReceiverEBox())) {
            mailLst.add(mo);

            // mo.set status je delivered
            // mail exists
            lstStatuses.add(s);
          } else {

            LOG.formatedError(
                    "In Message with embsId %s has invalid service, senderbox or receiverbox",
                    s.getRefToMessageId());
            continue;

          }

        }

      } catch (IOException ex) {
        String msg = "Error occured while generating report for data:  '" + file.
                getName() + "'!";
        LOG.logError(msg, ex);
        // move file to error folder
        moveFileToFolder(sendingFile, errorFolder);
        throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
                msg);

      }

      MailStatusReport ms = new MailStatusReport();
      ms.getMailStatuses().addAll(lstStatuses);
      ms.setDate(Calendar.getInstance().getTime());
      ms.setService(mRef.getService());
      ms.setMepsEBox(mRef.getSenderEBox());
      ms.setMepsName(mRef.getSenderName());

      MEPSPayloadPart srType = MEPSPayloadPart.STATUS_REPORT;

      MSHOutMail mout = new MSHOutMail();
      mout.setMessageId(Utils.getUUIDWithLocalDomain());
      mout.setService(mRef.getService());
      mout.setAction(MEPSAction.SERVICE_STATUS_NOTIFICATION.getValue());
      mout.setConversationId(Utils.getUUIDWithLocalDomain());
      mout.setSenderEBox(mRef.getReceiverEBox());
      mout.setSenderName(mRef.getReceiverName());

      mout.setReceiverEBox(mRef.getSenderEBox());
      mout.setReceiverName(mRef.getSenderName());
      mout.setSubject(srType.getName());
      // prepare mail to persist
      Date dt = Calendar.getInstance().getTime();
      // set current status
      mout.setStatus(SEDOutboxMailStatus.SUBMITTED.getValue());
      mout.setSubmittedDate(dt);
      mout.setStatusDate(dt);

      // create payload
      try {
        MSHOutPart pp = mmuUtils.createMSHOutPart(
                srType, ms);
        mout.setMSHOutPayload(new MSHOutPayload());
        mout.getMSHOutPayload().getMSHOutParts().add(pp);
      } catch (StorageException | JAXBException | IOException ex) {
        String msg = "Error occured while generating payload for data:  '" + file.
                getName() + "'!";
        LOG.logError(msg, ex);
        moveFileToFolder(sendingFile, errorFolder);
        throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
                msg);

      }

      try {
        // submit mail
        mdao.serializeOutMail(mout, "", AppConstant.PLUGIN_TYPE, null);
        moveFileToFolder(sendingFile, sentFolder);
      } catch (StorageException ex) {
        String msg = "Error occured while sending mail for data:  '" + file.
                getName() + "'!";
        LOG.logError(msg, ex);
        moveFileToFolder(sendingFile, errorFolder);
        throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
                msg);
      }

      // set status to all 
      String statusMsg = String.format("Mail report submitted in message: %s",
              mout.getMessageId());
      mailLst.stream().forEach((m) -> {
        try {

          mdao.setStatusToInMail(m, SEDInboxMailStatus.DELIVERED, statusMsg,
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

    }
    sw.append("Submited '" + iVal + "'");
    sw.append(" in : " + (LOG.getTime() - l) + " ms\n");
    LOG.logEnd(l);
    return sw.toString();
  }

  public Path moveFileToFolder(Path file, File folder) throws TaskException {
    Path newFile = null;
    try {

      newFile = folder.toPath().resolve(file.getFileName());
      Files.move(file, newFile);
    } catch (IOException ex) {
      String msg = "Error occured while moving file: '" + file.toString() + "' to  folder  '" + folder.
              getName() + "'!";
      LOG.logError(msg, ex);
      throw new TaskException(TaskException.TaskExceptionCode.ProcessException,
              msg);
    }
    return newFile;
  }

  public Status createStatus(final String line) throws TaskException {
    Status st = new Status();
    String[] values = line.split("\\|");
    int i = values.length - 8;

    //for (int i = 0, l = values.length; i < l; i++) {
    String v = values[i];
    if (Utils.isEmptyString(v) || !v.trim().equalsIgnoreCase("x")) {
      String msg = String.format(
              "Line '%s' do not have 7 proceeding data after delimiter |x|mass[integer]|level|postalId|internalStatus|packageId|status|status date[dd.mm.yyyy]|",
              line);
      LOG.logError(msg, null);
      throw new TaskException(
              TaskException.TaskExceptionCode.ProcessException, msg);

    }

    // 15. - RefToMessageId
    // 26. - SenderMessageId
    // 27. - packageId
    // od x date;
    //x + 0. x - "delimiter", od kje naprej so naši podatki
    //x + 1. [MASA] 14 - masa [g]
    //x + 2. 1(oz 9) - stopnja postopka preverjanja (za naše potrebe)
    //x + 3. [ODDAJNI POPIS] x(oz 69) - oddajni popis, vrnjen pri oddaji statusov na pošto
    //x + 4. OK - status uspešnosti preverjanja do trenutne stopnje (za naše potrebe)
    //x + 5. [ID PAKETA] - polje za recieved status
    //x + 6. [STATUS] - polje za processed/deleted/ignored status
    //x + 7. [DATUM ] - datum [dd.mm.yyyy]
    st.setRefToMessageId(values[14]); // shift for 1 (index start with 0)
    st.setSenderMessageId(
            values[25] != null && values[25].startsWith("#") ? values[25].
            substring(1) : values[25]); // skip #
    st.setProcessId(values[26]);

    st.setMass(new BigInteger(values[i + 1]));

    st.setPostalId(values[i + 3]);
    st.setStatus(values[i + 6]);
    st.setContentPageCount(BigInteger.ONE);
    try {
      st.setDate(mSDF.parse(values[i + 7].trim()));
    } catch (ParseException ex) {
      String msg = String.format(
              "Error parsing date: %s for line %s,  error: %s",
              values[i + 7], line, ex.getMessage());
      LOG.logError(msg, ex);
      throw new TaskException(
              TaskException.TaskExceptionCode.ProcessException, msg);
    }
    return st;

  }

  /**
   *
   * @return
   */
  @Override
  public CronTaskDef getDefinition() {
    CronTaskDef tt = new CronTaskDef();
    tt.setType("MEPSStatusSubmitter");
    tt.setName("MEPS status submitter");
    tt.setDescription(
            "Task submits statuses for processed mail. '");
    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_EXPORT_FOLDER,
            "Folder", true,
            PropertyType.String.getType(), null, null,
            "${laurentius.home}/meps/status/"));
    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_FILE_SUFFIX,
            "Status filename suffix", true,
            PropertyType.String.getType(), null, null, ".processed"));

    return tt;
  }

}
