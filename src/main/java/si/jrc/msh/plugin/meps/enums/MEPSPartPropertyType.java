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
package si.jrc.msh.plugin.meps.enums;

/**
 *
 * @author Joze Rihtarsic <joze.rihtarsic@sodisce.si>
 */
public enum MEPSPartPropertyType {

  /**
   *
   */
  RefPartEbmsId("http://www.sodisce.si/MEPS/RefPartEbmsId-%03d"),
  RefPartDigestSHA256("http://www.sodisce.si/MEPS/RefPartDigestSHA256-%03d"),  
  
  PartCreated("http://www.sodisce.si/MEPS/PartCreated"),
  PartPageCount("http://www.sodisce.si/MEPS/PartPageCount"),
  PartDocumentCount("http://www.sodisce.si/MEPS/DocumentCount"),
  
  ;
 



  String mstrType;


  private MEPSPartPropertyType(String val) {
    mstrType = val;
   
  }

  public String getType() {
    return mstrType;
  }
  
  public String getFormatedType(int i) {
    return String.format(mstrType, i);
  }


 
}
