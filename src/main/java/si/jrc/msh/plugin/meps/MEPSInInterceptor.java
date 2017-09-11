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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.transaction.UserTransaction;
import javax.xml.bind.JAXBException;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import si.jrc.msh.plugin.meps.enums.MEPSAction;
import si.jrc.msh.plugin.meps.enums.MEPSPartPropertyType;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.jrc.msh.plugin.meps.enums.MEPSService;
import si.jrc.msh.plugin.meps.exception.MEPSFault;
import si.jrc.msh.plugin.meps.exception.MEPSFaultCode;
import si.jrc.msh.plugin.meps.pdf.PDFUtil;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.msh.inbox.mail.MSHInMail;
import si.laurentius.commons.cxf.SoapUtils;
import si.laurentius.commons.enums.MimeValue;
import si.laurentius.commons.enums.SEDMailPartSource;
import si.laurentius.commons.enums.SEDOutboxMailStatus;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.SEDDaoInterface;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.Utils;
import si.laurentius.commons.utils.xml.XMLUtils;
import si.laurentius.meps.envelope.EnvelopeData;
import si.laurentius.meps.report.MailStatusReport;
import si.laurentius.meps.report.Status;
import si.laurentius.msh.inbox.payload.IMPartProperty;
import si.laurentius.msh.inbox.payload.MSHInPart;
import si.laurentius.msh.outbox.mail.MSHOutMail;
import si.laurentius.plugin.interceptor.MailInterceptorDef;
import si.laurentius.plugin.interfaces.SoapInterceptorInterface;

/**
 *
 * @author Joze Rihtarsic <joze.rihtarsic@sodisce.si>
 */
@Stateless
@Local(SoapInterceptorInterface.class)
@TransactionManagement(TransactionManagementType.BEAN)
public class MEPSInInterceptor implements SoapInterceptorInterface {

  /**
   *
   */
  protected final SEDLogger LOG = new SEDLogger(MEPSInInterceptor.class);

  PDFUtil pdfUtils = new PDFUtil();
  StorageUtils storageUtils = new StorageUtils();
  private final SimpleDateFormat mSDF = new SimpleDateFormat("dd.MM.yyyy");
  /**
   *
   */

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mdao;

  @Override
  public MailInterceptorDef getDefinition() {
    MailInterceptorDef def = new MailInterceptorDef();
    def.setType("MEPSInInterceptor");
    def.setName("MEPSInInterceptor");
    def.setDescription(
            "MEPS interceptor validate in mail with action ADDMail. For service requestor validate  status report");

    return def;
  }

  /**
   *
   * @param msg
   */
  @Override
  public boolean handleMessage(SoapMessage msg, Properties cp) {
    long l = LOG.logStart();
    boolean isBackChannel = SoapUtils.isRequestMessage(msg);
    MSHInMail mInMail = SoapUtils.getMSHInMail(msg);

    if (!isBackChannel) {
      if (mInMail != null && MEPSAction.ADD_MAIL.getValue().equals(mInMail.
              getAction())) {
        validateMail(mInMail);

      }

      if (mInMail != null && MEPSAction.SERVICE_STATUS_NOTIFICATION.getValue().
              equals(mInMail.
                      getAction())) {
        processStatusNotification(mInMail);

      }
    }

    return true;
  }

  public void processStatusNotification(MSHInMail mail) {
    // read report

    List<MSHInPart> lstParts = mail.getMSHInPayload().getMSHInParts();

    MSHInPart statusData = null;
    for (MSHInPart ip : lstParts) {
      if (Objects.equals(MEPSPayloadPart.STATUS_REPORT.getType(), ip.getType())) {
        if (statusData != null) {
          String msg = "Only one payload part for action 'ServiceStatusNotification' is expected. Got " + lstParts.
                  size();
          LOG.formatedWarning(
                  "Invalid request 'ServiceStatusNotification' from '%s'. Error: %s",
                  mail.getSenderEBox(), msg);
          throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
                  msg,
                  SoapFault.FAULT_CODE_SERVER);
        }
        statusData = ip;      
      }

    }

   

    MailStatusReport msr = null;
    try {
      msr = (MailStatusReport) XMLUtils.deserialize(
              StorageUtils.getFile(statusData.getFilepath()), MailStatusReport.class);

      // set statuses to sent mail
    } catch (JAXBException ex) {
      String msg = String.format(
              "Error occured while parsing  MailStatusReport '%s' ",
              ex.getMessage());
      LOG.formatedWarning(
              "Invalid request 'ServiceStatusNotification' from '%s'. Error: %s",
              mail.getSenderEBox(), msg);
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg,
              SoapFault.FAULT_CODE_SERVER);
    }

    List<Status> lst = msr.getMailStatuses();
    for (Status s : lst) {
      setStatusToMail(s);

    }

  }

  public void setStatusToMail(Status s) {

    List<MSHOutMail> mLst = mdao.getMailByMessageId(MSHOutMail.class, s.
            getRefToMessageId());

    if (mLst.size() != 1) {
      LOG.formatedError(
              "In Message with embsId %s not exist or more than one message. Status is ignored",
              s.getRefToMessageId());
      return;
    }
    MSHOutMail mo = mLst.get(0);
    SEDOutboxMailStatus status;

    String desc;
    switch (s.getStatus()) {
      case "PROCESSED":
        status = SEDOutboxMailStatus.DELIVERED;
        desc = String.format(
                "Mail processed by service provider (mass %d g, procId: %s, postalId: %s,  date %s): ",
                s.getMass(), s.getProcessId(),
                s.getPostalId(), mSDF.format(s.getDate()));

        break;
      case "DELETED":
        status = SEDOutboxMailStatus.DELETED;
        desc = "Deleted by service provider:" + s.getErrDesc();
        break;
      case "ERROR":
        status = SEDOutboxMailStatus.ERROR;
        desc = s.getErrDesc();
        break;
      default:
        String msg = String.format("Unknown status: %s for mail %s", s.
                getStatus(), s.getRefToMessageId());
        throw new MEPSFault(MEPSFaultCode.Other, s.getRefToMessageId(),
                msg,
                SoapFault.FAULT_CODE_SERVER);
    }

    try {
      mdao.setStatusToOutMail(mo, status, desc, null, AppConstant.PLUGIN_TYPE);
    } catch (StorageException ex) {
      LOG.logError("Error occured while setting status to out mail: " + mo.
              getMessageId(), ex);
    }

  }

  public void validateMail(MSHInMail mail) {
    // validate data
    // - has pdf payload
    // - has envelope data
    // Validate envelope data
    // - sender address
    // - receiver address
    // - has r number
    // - receiver is in slovenija

    if (mail.getMSHInPayload() == null || mail.getMSHInPayload().
            getMSHInParts().isEmpty()
            || mail.getMSHInPayload().getMSHInParts().size() < 2) {
      String msg = "Mail must have two attachmetns (Envelope data (xml) and one content pdf)";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg,
              SoapFault.FAULT_CODE_CLIENT);

    }

    // sort mail payload by type
    MSHInPart envDataPart = null;
    MSHInPart concatenatedPart = null;
    for (MSHInPart mp : mail.getMSHInPayload().getMSHInParts()) {
      if (!Objects.equals(mp.getSource(), SEDMailPartSource.MAIL.getValue())) {
        // ignore ebms payloads
        continue;
      }

      if ((Objects.equals(MimeValue.MIME_XML.getMimeType(), mp.getMimeType())
              || Objects.equals(MimeValue.MIME_XML1.getMimeType(), mp.
                      getMimeType()))
              && Objects.equals(mp.getType(), MEPSPayloadPart.ENVELOPE_DATA.
                      getType())) {
        if (envDataPart != null) {
          throw new MEPSFault(MEPSFaultCode.Other, null,
                  "Mail must have only one XML  attachmetns (Envelope data)!",
                  SoapFault.FAULT_CODE_CLIENT);
        } else {
          envDataPart = mp;
        }
      } else if (Objects.equals(MimeValue.MIME_PDF.getMimeType(), mp.
              getMimeType())
              && Objects.equals(mp.getType(),
                      MEPSPayloadPart.CONCATENATED_CONTENT.
                              getType())) {
        if (concatenatedPart != null) {
          throw new MEPSFault(MEPSFaultCode.Other, null,
                  "Mail must have only one PDF content file!",
                  SoapFault.FAULT_CODE_CLIENT);
        } else {
          concatenatedPart = mp;
        }

      } else {
        String msg = "Mail must have only one XML (type: " + MEPSPayloadPart.ENVELOPE_DATA.
                getType() + ") and one PDF (printable content) attachmetns (type: " + MEPSPayloadPart.CONCATENATED_CONTENT.
                        getType() + ")!";
        throw new MEPSFault(MEPSFaultCode.Other, null,
                msg,
                SoapFault.FAULT_CODE_CLIENT);
      }

    }

    if (envDataPart == null) {
      String msg = "Missing envelope data!";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg, SoapFault.FAULT_CODE_CLIENT);
    }
    if (concatenatedPart == null) {
      String msg = "Missing content pdf!";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg, SoapFault.FAULT_CODE_CLIENT);
    }

    EnvelopeData envData;
    try {
      // parse envelope data
      envData = (EnvelopeData) XMLUtils.deserialize(StorageUtils.
              getFile(envDataPart.getFilepath()), EnvelopeData.class);

    } catch (JAXBException ex) {
      String msg = "Error occured while parsing EnvelopeData! Error: " + ex.
              getMessage();
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, ex,
              SoapFault.FAULT_CODE_CLIENT);
    }

    // validate envelope data by schema
    String res = XMLUtils.validateBySchema(envData, MEPSOutInterceptor.class.
            getResourceAsStream("/schemas/envelope-data.xsd"), null);

    if (!Utils.isEmptyString(res)) {
      String msg = "Invalid evelope data! Error: " + res;
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              res, null,
              SoapFault.FAULT_CODE_CLIENT);
    }

    MEPSService ms = MEPSService.getValueByService(mail.getService());
    if (ms == null) {
      String msg = String.
              format("Service %s is not implemented by this plugin!", mail.
                      getService());
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }

    // test pagecount and print service!    
    int iPageCnt = Integer.parseInt(getPartProperty(concatenatedPart,
            MEPSPartPropertyType.PartPageCount.getType()));

    if (ms.getMinPageCount() > iPageCnt) {
      String msg = String.format(
              "Min page count for service %s is  %d. Mail has %d pages!", mail.
                      getService(), ms.getMinPageCount(), iPageCnt);
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }
    if (ms.getMaxPageCount() < iPageCnt) {
      String msg = String.format(
              "Max page count for service %s is  %d. Mail has %d pages!", mail.
                      getService(), ms.getMaxPageCount(), iPageCnt);
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }

    int iPDFReadedPageCount;
    try {
      // test "read pdf" and  page count!
      iPDFReadedPageCount = pdfUtils.getFilePageCount(StorageUtils.getFile(
              concatenatedPart.getFilepath()));
    } catch (IOException ex) {
      String msg = "Error parsing pdf content!";
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, ex,
              SoapFault.FAULT_CODE_CLIENT);
    }

    if (iPDFReadedPageCount != iPageCnt) {
      String msg = "Readed page count do not match evelope page count!";
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }

    if (ms.hasUPNCode()) {

      if (envData.getPostalData() == null
              || envData.getPostalData().getUPNCode() == null
              || envData.getPostalData().getUPNCode().getNumber() == null
              || envData.getPostalData().getUPNCode().getControl() == null
              || envData.getPostalData().getUPNCode().getPrefix() == null
              || envData.getPostalData().getUPNCode().getSuffix() == null) {
        String msg = String.format(
                "Service %s  requires UPN code. Envelope data do not contain (complete) upn code!",
                mail.getService());
        throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
                msg, null,
                SoapFault.FAULT_CODE_CLIENT);
      }

    }

  }

  public String getPartProperty(MSHInPart part, String propertytype) {
    if (part == null || part.getIMPartProperties().isEmpty()) {
      return null;
    }
    for (IMPartProperty p : part.getIMPartProperties()) {
      if (Objects.equals(p.getName(), propertytype)) {
        return p.getValue();
      }
    }
    return null;
  }

  /**
   *
   * @param t
   */
  @Override
  public void handleFault(SoapMessage t, Properties cp) {
    // ignore
  }

}
