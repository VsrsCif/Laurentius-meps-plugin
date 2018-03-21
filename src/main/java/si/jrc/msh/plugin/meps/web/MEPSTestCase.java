/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package si.jrc.msh.plugin.meps.web;

import java.io.Serializable;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.primefaces.context.RequestContext;
import si.jrc.msh.plugin.meps.web.dlg.DialogProgress;
import si.laurentius.commons.utils.SEDLogger;

@SessionScoped
@Named("mepsTestCase")
public class MEPSTestCase extends MEPSTestAbstract implements Serializable {

  private static final SEDLogger LOG = new SEDLogger(MEPSTestCase.class);
  private final ProcessPackageCase tesPackage = new ProcessPackageCase(this);
  
  @Inject
  private MEPSLookups pluginLookups;

  public MEPSLookups getPluginLookups() {
    return pluginLookups;
  }

  public void setPluginLookups(MEPSLookups pluginLookups) {
    this.pluginLookups = pluginLookups;
  }
  
  
  public ProcessPackageCase getTestPackage() {
    return tesPackage;
  }
  
  

  public void executePackageTest() {
    long l = LOG.logStart();
    tesPackage.setProcessMessage("");
    tesPackage.setProgress(0);
    
    if (!tesPackage.validateData()) {
      return;
    }
    
    tesPackage.prepareToStart();
    // show progress dialog
    DialogProgress dlg = getDlgProgress();
    dlg.setProcess(tesPackage);
    
    RequestContext context = RequestContext.getCurrentInstance();
    context.execute("PF('dlgPrgBar').start();");
    context.execute("PF('dialogProgress').show();");    
    context.update(":dlgProgress:dlgProgressForm:pnlProgress");
    tesPackage.executeStressTest();
    LOG.logEnd(l);
  }
  
  
  
  
 
  

  
}
