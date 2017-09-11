/*
* Copyright 2017, Supreme Court Republic of Slovenia 
*
* Licensed under the EUPL, Version 1.1 or – as soon they will be approved by 
* the European Commission - subsequent versions of the EUPL (the "Licence");
* You may not use this work except in compliance with the Licence.
* You may obtain a copy of the Licence at:
*
* https://joinup.ec.europa.eu/software/page/eupl
*
* Unless required by applicable law or agreed to in writing, software 
* distributed under the Licence is distributed on an "AS IS" basis, WITHOUT 
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the Licence for the specific language governing permissions and  
* limitations under the Licence.
 */
package si.jrc.msh.plugin.meps.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import si.jrc.msh.plugin.meps.enums.MEPSAction;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.jrc.msh.plugin.meps.enums.MEPSService;
import si.laurentius.commons.enums.MimeValue;
import si.laurentius.commons.pmode.enums.MEPChannelBindingType;
import si.laurentius.msh.pmode.Action;
import si.laurentius.msh.pmode.MEPLegType;
import si.laurentius.msh.pmode.MEPTransportType;
import si.laurentius.msh.pmode.MEPType;
import si.laurentius.msh.pmode.PMode;
import si.laurentius.msh.pmode.PayloadProfile;
import si.laurentius.msh.pmode.Service;
import si.laurentius.msh.pmode.TransportChannelType;

/**
 *
 * @author Jože Rihtaršič
 */
public class PModeUtils {

  public static String INITIAL_ROLE = "ServiceRequestor";
  public static String EXECUTOR_ROLE = "ServiceProvider";

  public static String SECURITY_MEP_ID = "sign_sha256";
  public static String REC_AW_MEP_ID = "AS4ReceiptResponse";

  public List<Service> createServices() {
    List<Service> lst = new ArrayList<>();
    for (MEPSService ms : MEPSService.values()) {
      Service srv = new Service();
      srv.setId(ms.getService());
      srv.setServiceName(ms.getService());
      srv.setServiceType(ms.getNamespace());
      srv.setUseSEDProperties(Boolean.TRUE);
      // set roles      
      srv.setExecutor(new Service.Executor());
      srv.setInitiator(new Service.Initiator());
      srv.getExecutor().setRole(EXECUTOR_ROLE);
      srv.getInitiator().setRole(INITIAL_ROLE);

      // add actions
      Action actAddMail = new Action();
      actAddMail.setName(MEPSAction.ADD_MAIL.getValue());
      actAddMail.setInvokeRole("initiator");
      actAddMail.setMessageType("userMessage");
      actAddMail.setPayloadProfiles(new Action.PayloadProfiles());
      actAddMail.getPayloadProfiles().setMaxSize(BigInteger.valueOf(10485760));
      PayloadProfile pf1 = new PayloadProfile();
      pf1.setMIME(MimeValue.MIME_XML.getMimeType());
      pf1.setName(MEPSPayloadPart.ENVELOPE_DATA.getName());
      pf1.setMaxOccurs(1);
      pf1.setMinOccurs(1);
      pf1.setMaxSize(BigInteger.valueOf(10485760));

      PayloadProfile pf2 = new PayloadProfile();
      pf2.setMIME(MimeValue.MIME_PDF.getMimeType());
      pf2.setName(MEPSPayloadPart.CONCATENATED_CONTENT.getName());
      pf2.setMaxOccurs(1);
      pf2.setMinOccurs(1);
      pf1.setMaxSize(BigInteger.valueOf(10485760));
      actAddMail.getPayloadProfiles().getPayloadProfiles().add(pf1);
      actAddMail.getPayloadProfiles().getPayloadProfiles().add(pf2);

      // remove  actions
      Action actRemove = new Action();
      actRemove.setName(MEPSAction.REMOVE_MAIL.getValue());
      actRemove.setInvokeRole("initiator");
      actRemove.setMessageType("signalMessage");
      actRemove.setPayloadProfiles(new Action.PayloadProfiles());
      actRemove.getPayloadProfiles().setMaxSize(BigInteger.valueOf(10485760));
      PayloadProfile pfRM = new PayloadProfile();
      pfRM.setMIME(MimeValue.MIME_XML.getMimeType());
      pfRM.setName(MEPSPayloadPart.REMOVE_MAIL.getName());
      pfRM.setMaxOccurs(1);
      pfRM.setMinOccurs(1);
      pfRM.setMaxSize(BigInteger.valueOf(10485760));
      actRemove.getPayloadProfiles().getPayloadProfiles().add(pfRM);

      // remove  actions
      Action actStatusReport = new Action();
      actStatusReport.setName(MEPSAction.SERVICE_STATUS_NOTIFICATION.getValue());
      actStatusReport.setInvokeRole("signalMessage");
      actStatusReport.setMessageType("initiator");
      actStatusReport.setPayloadProfiles(new Action.PayloadProfiles());
      actStatusReport.getPayloadProfiles().setMaxSize(BigInteger.valueOf(
              10485760));
      PayloadProfile pfSR = new PayloadProfile();
      pfSR.setMIME(MimeValue.MIME_XML.getMimeType());
      pfSR.setName(MEPSPayloadPart.STATUS_REPORT.getName());
      pfSR.setMaxOccurs(1);
      pfSR.setMinOccurs(1);
      pfSR.setMaxSize(BigInteger.valueOf(10485760));
      actStatusReport.getPayloadProfiles().getPayloadProfiles().add(pfSR);

      srv.getActions().add(actAddMail);
      srv.getActions().add(actRemove);
      srv.getActions().add(actStatusReport);

      lst.add(srv);
    }
    return lst;
  }

  public List<PMode> createPModes() {
    List<PMode> lst = new ArrayList<>();
    for (MEPSService ms : MEPSService.values()) {
      PMode pmd = new PMode();
      pmd.setId(ms.getService());
      pmd.setServiceIdRef(ms.getService());
      MEPChannelBindingType mcbPush = MEPChannelBindingType.Push;

      MEPType mtAddMail = new MEPType();
      mtAddMail.setMEPChannelBinding(mcbPush.getValue());
      mtAddMail.setMEPType(mcbPush.getMepType().getValue());
      mtAddMail.setMEPInitiatorRole(INITIAL_ROLE);
      MEPLegType mplAddMail = new MEPLegType();
      mplAddMail.setMPC(
              "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPC");
      mplAddMail.setTransport(new MEPTransportType());
      mplAddMail.getTransport().setForeChannel(new TransportChannelType());
      mplAddMail.getTransport().getForeChannel().setAction(MEPSAction.ADD_MAIL.
              getValue());
      mplAddMail.getTransport().getForeChannel().setReceptionAwareness(
              new TransportChannelType.ReceptionAwareness());
      mplAddMail.getTransport().getForeChannel().getReceptionAwareness().
              setRaPatternIdRef(REC_AW_MEP_ID);
      mplAddMail.getTransport().getForeChannel().setSecurityIdRef(
              SECURITY_MEP_ID);
      mtAddMail.getLegs().add(mplAddMail);

      MEPType mtRemoveMail = new MEPType();
      mtRemoveMail.setMEPChannelBinding(mcbPush.getValue());
      mtRemoveMail.setMEPType(mcbPush.getMepType().getValue());
      mtRemoveMail.setMEPInitiatorRole(INITIAL_ROLE);
      MEPLegType mplRemove = new MEPLegType();
      mplRemove.setMPC(
              "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPC");
      mplRemove.setTransport(new MEPTransportType());
      mplRemove.getTransport().setForeChannel(new TransportChannelType());
      mplRemove.getTransport().getForeChannel().setAction(
              MEPSAction.REMOVE_MAIL.getValue());
      mplRemove.getTransport().getForeChannel().setReceptionAwareness(
              new TransportChannelType.ReceptionAwareness());
      mplRemove.getTransport().getForeChannel().getReceptionAwareness().
              setRaPatternIdRef(REC_AW_MEP_ID);
      mplRemove.getTransport().getForeChannel().
              setSecurityIdRef(SECURITY_MEP_ID);
      mtRemoveMail.getLegs().add(mplRemove);

      MEPType mtReport = new MEPType();
      mtReport.setMEPChannelBinding(mcbPush.getValue());
      mtReport.setMEPType(mcbPush.getMepType().getValue());
      mtReport.setMEPInitiatorRole(EXECUTOR_ROLE);
      MEPLegType mplReport = new MEPLegType();
      mplReport.setMPC(
              "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/defaultMPC");
      mplReport.setTransport(new MEPTransportType());
      mplReport.getTransport().setForeChannel(new TransportChannelType());
      mplReport.getTransport().setBackChannel(new TransportChannelType());
      
      mplReport.getTransport().getForeChannel().setAction(
              MEPSAction.SERVICE_STATUS_NOTIFICATION.getValue());
      mplReport.getTransport().getForeChannel().setReceptionAwareness(
              new TransportChannelType.ReceptionAwareness());
      mplReport.getTransport().getForeChannel().getReceptionAwareness().
              setRaPatternIdRef(REC_AW_MEP_ID);
      mplReport.getTransport().getForeChannel().
              setSecurityIdRef(SECURITY_MEP_ID);
      
      mplReport.getTransport().getBackChannel().
              setSecurityIdRef(SECURITY_MEP_ID);
      
      mtReport.getLegs().add(mplReport);

      pmd.getMEPS().add(mtAddMail);
      pmd.getMEPS().add(mtRemoveMail);
      pmd.getMEPS().add(mtReport);

      lst.add(pmd);
    }
    return lst;
  }
}
