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
package si.jrc.msh.plugin.meps.enums;

import java.util.Objects;

/**
 *
 * @author Jože Rihtaršič
 */
public enum MEPSService {
  
  LegalZPP("PrintAndEnvelope-LegalZPP", "http://laurentius.si/meps", "ZPP", 1, 800, true),  // - max 2kg
  LegalZPP_NP("PrintAndEnvelope-LegalZPP_NP", "http://laurentius.si/meps", "ZPN", 1, 800, true), // - max 2kg
  LegalZSR("PrintAndEnvelope-LegalZSR", "http://laurentius.si/meps", "ZSR", 1, 800, true),  // - max 2kg  - zsreg
  LegalZFP("PrintAndEnvelope-LegalZFP¸", "http://laurentius.si/meps", "ZFP", 1, 800, true),  // - max 2kg - ZFPPIPP 
  LegalZUP("PrintAndEnvelope-LegalZUP", "http://laurentius.si/meps", "ZUP", 1, 800, true), // - max 2kg
  C5("PrintAndEnvelope-C5", "http://laurentius.si/meps", "NAV", 1, 800, false), // - max 2kg
  C5_R("PrintAndEnvelope-C5-R", "http://laurentius.si/meps", "NAR", 1, 800, true), // - max 2kg
  C5_AD("PrintAndEnvelope-C5-AD", "http://laurentius.si/meps", "NAP", 1, 800, true), // - max 2kg
  Package("PrintAndEnvelope-Package", "http://laurentius.si/meps", "PAK", 800, 12000, false), // 2kg  - 30kg
  Package_R("PrintAndEnvelope-Package_R", "http://laurentius.si/meps", "PKR", 800, 12000, true),//2kg -  30kg
  Package_AD("PrintAndEnvelope-Package_AD", "http://laurentius.si/meps", "PKP", 800, 12000, true);// 2kg -30kg
  
  
  
  
  
  String service;
  String namespace;
  String envName;
  int minPageCount;
  int maxPageCount;
  boolean hasUPNCode;
  private MEPSService(String service, String namespace, String envName, int minPageCount, int maxPageCount,boolean hasUPNCode){
    this.service = service;
    this.namespace = namespace; 
    this.envName = envName;
    this.hasUPNCode = hasUPNCode;
    this.minPageCount = minPageCount;
    this.maxPageCount = maxPageCount;
    
  }
  
  

  public String getService() {
    return service;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getEnvelopeName() {
    return envName;
  }

  public boolean hasUPNCode() {
    return hasUPNCode;
  }

  public int getMinPageCount() {
    return minPageCount;
  }

  public int getMaxPageCount() {
    return maxPageCount;
  }
  
  public static MEPSService getValueByService(String service){
    for (MEPSService ms: values()){
      if (Objects.equals(ms.getService(),service) ){
        return ms;
      }
    }
    return null;
  
  }
  
  public static String[] serviceNames(){
    MEPSService[] v = values();
    String[] srvs = new String[v.length];
    for (int i =0; i< v.length; i++){
      srvs[i] = v[i].getService();
    }
    return srvs;
  
  }

  
  
}
