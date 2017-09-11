/*
 * Copyright 2016, Supreme Court Republic of Slovenia
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
package si.jrc.msh.plugin.meps.web;

import java.util.List;
import java.util.Objects;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import si.jrc.msh.plugin.meps.ejb.MEPSDataInterface;
import si.jrc.msh.plugin.meps.enums.MEPSService;
import si.laurentius.commons.SEDSystemProperties;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.plugin.meps.PartyType;
import si.laurentius.plugin.meps.PhysicalAddressType;


/**
 *
 * @author Jože Rihtaršič
 */
@SessionScoped
@ManagedBean(name = "MEPSLookups")
public class MEPSLookups {

  private static final SEDLogger LOG = new SEDLogger(MEPSLookups.class);

  @EJB(mappedName = "java:global/plugin-meps/MEPSDataBean!si.jrc.msh.plugin.meps.ejb.MEPSDataInterface")
  MEPSDataInterface mdata;

  

  public String getLocalDomain() {
    return SEDSystemProperties.getLocalDomain();
  }

  /**
   *
   * @return
   */
  protected FacesContext facesContext() {
    return FacesContext.getCurrentInstance();
  }

  public MEPSService[] getServices() {
    return MEPSService.values();
  }
  
  


  public List<PhysicalAddressType> getAddresses() {
    return mdata.getAddresses();
  }

  public PhysicalAddressType getSenderAddress() {
    return mdata.getSenderAddress();
  }
  
  public PartyType.PostContract getPostContract() {
    PartyType p = mdata.getParty();
    return p!= null?p.getPostContract():null;
  }
  
  public PartyType.ServiceProviderContract getServiceProviderContract() {
    PartyType p = mdata.getParty();
    return p!= null?p.getServiceProviderContract():null;
  }
  
}
