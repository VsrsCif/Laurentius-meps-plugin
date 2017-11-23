/*
 * Copyright 2017, Supreme Court Republic of Slovenia
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

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.transaction.UserTransaction;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.MessageUtils;
import si.jrc.msh.plugin.meps.enums.MEPSAction;
import si.jrc.msh.plugin.meps.enums.MEPSPartPropertyType;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.jrc.msh.plugin.meps.enums.MEPSService;
import si.jrc.msh.plugin.meps.exception.MEPSFault;
import si.jrc.msh.plugin.meps.exception.MEPSFaultCode;
import si.jrc.msh.plugin.meps.pdf.PDFContentData;
import si.jrc.msh.plugin.meps.pdf.PDFException;
import si.jrc.msh.plugin.meps.pdf.PDFUtil;
import si.jrc.msh.plugin.meps.utils.MEPSUtils;
import si.laurentius.msh.outbox.mail.MSHOutMail;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.cxf.SoapUtils;
import si.laurentius.commons.enums.MimeValue;
import si.laurentius.commons.enums.SEDOutboxMailStatus;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.SEDDaoInterface;
import si.laurentius.commons.pmode.EBMSMessageContext;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.Utils;
import si.laurentius.commons.utils.xml.XMLUtils;
import si.laurentius.meps.envelope.EnvelopeData;
import si.laurentius.meps.envelope.PrintContent;
import si.laurentius.msh.outbox.payload.MSHOutPart;
import si.laurentius.plugin.interceptor.MailInterceptorDef;
import si.laurentius.plugin.interfaces.SoapInterceptorInterface;

/**
 * Interceptor validates and concatenate all pdf's to one pdf as printable
 * content for mail. "Concatenation" adds blank page on all pdf documents with
 * odd number of pages. (Printing is on both side of sheet of paper. Blank page
 * ensures that each pdf document starts on new sheet of paper..
 *
 *
 * @author Joze Rihtarsic <joze.rihtarsic@sodisce.si>
 */
@Stateless
@Local(SoapInterceptorInterface.class)
@TransactionManagement(TransactionManagementType.BEAN)
public class MEPSOutInterceptor implements SoapInterceptorInterface {
  
  protected final SEDLogger LOG = new SEDLogger(MEPSOutInterceptor.class);

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mDB;
  @Resource
  public UserTransaction mutUTransaction;

  /**
   *
   */
  private PDFUtil pdfUtils = new PDFUtil();
  private StorageUtils storageUtils = new StorageUtils();
  private MEPSUtils mmuUtils = new MEPSUtils();
  

  @Override
  public MailInterceptorDef getDefinition() {
    MailInterceptorDef mid = new MailInterceptorDef();
    mid.setType("MEPSOutInterceptor");
    mid.setName("MEPSOutInterceptor");
    mid.setDescription(
            "MEPS Out mail interceptor for preparing outmail to meps process");
    return mid;
  }

  /**
   *
   * @param t
   */
  @Override
  public void handleFault(SoapMessage t, Properties cp) {
    // ignore
  }

  /**
   *
   * @param msg
   */
  @Override
  public boolean handleMessage(SoapMessage msg, Properties cp) {
    long l = LOG.logStart(msg);

    boolean isRequest = MessageUtils.isRequestor(msg);
    QName sv = (isRequest ? SoapFault.FAULT_CODE_CLIENT : SoapFault.FAULT_CODE_SERVER);

    EBMSMessageContext ectx = SoapUtils.getEBMSMessageOutContext(msg);
    MSHOutMail outMail = SoapUtils.getMSHOutMail(msg);

    if (isRequest && outMail!= null && Objects.equals(outMail.getAction(), MEPSAction.ADD_MAIL.getValue())){    
      handleAddMail(outMail);
    }

    LOG.logEnd(l);
    return true;
  }

  /**
   *
   * @param mail
   */
  public void handleAddMail(MSHOutMail mail) {
    // validate data
    // - has pdf payload
    // - has envelope data
    // Validate envelope data
    // - sender address
    // - receiver address
    // - has r number
    // - receiver is in slovenija

    if (mail.getMSHOutPayload() == null || mail.getMSHOutPayload().
            getMSHOutParts().isEmpty()
            || mail.getMSHOutPayload().getMSHOutParts().size() < 2) {
      String msg = "Mail must have at least two attachmetns (Envelope data (xml) and one or more content pdf)";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg,
              SoapFault.FAULT_CODE_CLIENT);

    }

    // sort mail payload by type
    MSHOutPart envDataPart = null;
    MSHOutPart concatenatedPart = null;
    List<MSHOutPart> lstPdfContent = new ArrayList<>();

    List<MSHOutPart> lstUpdatePart = new ArrayList<>();
    List<MSHOutPart> lstAddPart = new ArrayList<>();

    for (MSHOutPart mp : mail.getMSHOutPayload().getMSHOutParts()) {

      if (Objects.equals(MimeValue.MIME_XML.getMimeType(), mp.getMimeType())
              || Objects.equals(MimeValue.MIME_XML1.getMimeType(), mp.
                      getMimeType())) {
        if (envDataPart != null) {
          throw new MEPSFault(MEPSFaultCode.Other, null,
                  "Mail must have only one XML  attachmetns (Envelope data)!",
                  SoapFault.FAULT_CODE_CLIENT);
        } else {
          envDataPart = mp;
        }
      } else if (Objects.equals(MimeValue.MIME_PDF.getMimeType(), mp.
              getMimeType())) {
        if (Objects.equals(mp.getType(), MEPSPayloadPart.CONCATENATED_CONTENT.
                getType())) {
          concatenatedPart = mp;
        } else {
          // set part as not sent - only Concatenated pdf will be sent
          if (mp.getIsSent()) {
            mp.setIsSent(Boolean.FALSE);
            lstUpdatePart.add(mp);
          }
          // add content
          lstPdfContent.add(mp);
        }
      } else {
        String msg = "Mail must have only one XML (Envelope data) and PDF (printable content) attachmetns!";
        throw new MEPSFault(MEPSFaultCode.Other, null,
                msg,
                SoapFault.FAULT_CODE_CLIENT);
      }

    }

    if (envDataPart == null) {
      String msg = "Missing envelope data!";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg,
              SoapFault.FAULT_CODE_CLIENT);
    }
    if (concatenatedPart == null) {
      concatenatedPart = createConcatenatedPdf(mail, lstPdfContent);
      lstAddPart.add(concatenatedPart);
    }

    EnvelopeData envData;
    File envDataFile =  StorageUtils.
              getFile(envDataPart.getFilepath());
    try {
      // parse envelope data
      envData = (EnvelopeData) XMLUtils.deserialize(envDataFile, EnvelopeData.class);

    } catch (JAXBException ex) {
      String msg = "Error occured while parsing EnvelopeData! Error: " + ex.
              getMessage();
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, ex,
              SoapFault.FAULT_CODE_CLIENT);
    }

    // validate by schema
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
    int iPageCnt = Integer.parseInt(mmuUtils.getPartProperty(concatenatedPart,
            MEPSPartPropertyType.PartPageCount.getType()));

    if (ms.getMinPageCount() > iPageCnt) {
      String msg = String.format(
              "Min page count for service %s is  %d. Mail has %d pages! ", mail.
                      getService(), ms.getMinPageCount(), iPageCnt);
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }
    if (ms.getMaxPageCount() < iPageCnt) {
      String msg = String.format(
              "Max page count for service %s is  %d. Mail %d has pages!", mail.
                      getService(), ms.getMaxPageCount(), iPageCnt);
      throw new MEPSFault(MEPSFaultCode.Other, mail.getMessageId(),
              msg, null,
              SoapFault.FAULT_CODE_CLIENT);
    }

    // set print content
    envData.getPrintContents().clear();
    PrintContent pc = new PrintContent();
    pc.setPageCount(BigInteger.valueOf(iPageCnt));
    pc.setFilename(concatenatedPart.getFilename());
    pc.setMimeType(concatenatedPart.getMimeType());
    pc.setHashSHA256(concatenatedPart.getSha256Value());
    envData.getPrintContents().add(pc);
    envDataPart.setType(MEPSPayloadPart.ENVELOPE_DATA.getType());
    envDataPart.setIsSent(Boolean.TRUE);

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
    // update if not added
    if (!lstAddPart.contains(envDataPart)){
      lstUpdatePart.add(envDataPart);
    }

     
    
    try {
      

      XMLUtils.serialize(envData, envDataFile );
      mDB.updateOutMailPayload(mail, lstAddPart,
              lstUpdatePart, Collections.emptyList(),
              SEDOutboxMailStatus.PROCESS, "Prepare mail to print service",
              null, AppConstant.PLUGIN_TYPE);
    } catch (JAXBException |FileNotFoundException|  StorageException ex) {
      String msg = "Error occured while store concatenated content and updated envelopeData!";
      throw new MEPSFault(MEPSFaultCode.Other, null,
              msg,
              SoapFault.FAULT_CODE_CLIENT);
    } 

    // clear payload
    mail.getMSHOutPayload().getMSHOutParts().clear();
    // add only envData and concatenated content
    mail.getMSHOutPayload().getMSHOutParts().add(envDataPart);
    mail.getMSHOutPayload().getMSHOutParts().add(concatenatedPart);

  }

  public MSHOutPart createConcatenatedPdf(MSHOutMail mail,
          List<MSHOutPart> lstPdfContent) {
    MSHOutPart mp;
    List<File> lstFiles = new ArrayList<>();
    for (MSHOutPart op : lstPdfContent) {
      lstFiles.add(StorageUtils.getFile(op.getFilepath()));
    }

    try {
      
      PDFContentData pd = pdfUtils.concatenatePdfFiles(lstFiles);
      File f = storageUtils.storeOutFile(MEPSPayloadPart.CONCATENATED_CONTENT.
              getMimeType(), new File(pd.getTempFileName()));
      // delete temp file
      pd.deleteTempFile();

      mp = new MSHOutPart();
      mp.setIsReceived(Boolean.FALSE);
      mp.setIsSent(Boolean.TRUE);

      mp.setEbmsId(Utils.getUUIDWithLocalDomain());
      mp.setFilename(String.format(MEPSPayloadPart.CONCATENATED_CONTENT.
              getFilename(), mail.getId()));
      mp.setName(MEPSPayloadPart.CONCATENATED_CONTENT.getName());
      mp.setDescription(
              MEPSPayloadPart.CONCATENATED_CONTENT.getName() + " Page count: " + pd.
              getPageCount());
      mp.setType(MEPSPayloadPart.CONCATENATED_CONTENT.getType());
      mp.setMimeType(MEPSPayloadPart.CONCATENATED_CONTENT.getMimeType());
      mp.setFilepath(StorageUtils.getRelativePath(f));
      mp.setSource(AppConstant.PLUGIN_TYPE);

      mmuUtils.addOrUpdatePartProperty(mp, MEPSPartPropertyType.PartCreated.getType(),
              LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
      mmuUtils.addOrUpdatePartProperty(mp, MEPSPartPropertyType.PartDocumentCount.
              getType(), pd.getDocumentCount() + "");
      mmuUtils.addOrUpdatePartProperty(mp, MEPSPartPropertyType.PartPageCount.
              getType(), pd.getPageCount() + "");

      for (int i = 0, l = lstPdfContent.size(); i < l; i++) {
        mmuUtils.addOrUpdatePartProperty(mp, MEPSPartPropertyType.RefPartEbmsId.
                getFormatedType(i), lstPdfContent.get(i).getEbmsId());
        mmuUtils.addOrUpdatePartProperty(mp, MEPSPartPropertyType.RefPartDigestSHA256.
                getFormatedType(i), lstPdfContent.get(i).getSha256Value());
      }

    } catch (PDFException | StorageException ex) {
      throw new MEPSFault(MEPSFaultCode.Other, null,
              "Process exception: " + ex.getMessage(), ex,
              SoapFault.FAULT_CODE_CLIENT);
    }
    return mp;
  }

 

}
