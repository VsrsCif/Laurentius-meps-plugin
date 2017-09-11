/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package si.jrc.msh.plugin.meps;

import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

/**
 *
 * @author Jože Rihtaršič
 */
@ApplicationScoped
@ManagedBean(name = "AppConstant")
public class AppConstant {

  
  /**
   *
   */
  public static final String S_PANEL_TEST = "S_PANEL_TEST";
  
  public static final String PLUGIN_TYPE = "MEPS";
  
  
  public static final String ROOT_FOLDER = "/meps/";
  public static final String BLOB_FOLDER = ROOT_FOLDER + "/test-pdf/";
  public static final String FILE_INIT_DATA = "meps-data.xml";
  public static final String FILE_PACKAGE_NUMBER = "last_package_number.txt";
  
  

  public String getS_PANEL_TEST() {
    return S_PANEL_TEST;
  }
}