/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package si.jrc.msh.plugin.meps.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import si.jrc.msh.plugin.meps.AppConstant;
import static si.jrc.msh.plugin.meps.AppConstant.ROOT_FOLDER;
import si.jrc.msh.plugin.meps.exception.MEPSException;
import si.laurentius.commons.SEDSystemProperties;
import si.laurentius.commons.utils.FileUtils;
import si.laurentius.commons.utils.SEDLogger;

/**
 *
 * @author sluzba
 */
public class PackageNumberGenerator {

  private static final SEDLogger LOG = new SEDLogger(
          PackageNumberGenerator.class);

  private int c = 0;

  public static PackageNumberGenerator mInstance;

  private PackageNumberGenerator() {
  }

  public static synchronized PackageNumberGenerator getInstance() {
    return mInstance == null ? mInstance = new PackageNumberGenerator() : mInstance;
  }

  public synchronized int getNextValue() throws MEPSException {
    Integer iCurr = null;
    File fCurValue = getNumberFile();

    if (fCurValue.exists()) {
      try {
        iCurr = Files.lines(fCurValue.toPath())
                .findFirst()
                .map(Integer::parseInt)
                .get();

      } catch (IOException ex) {
        LOG.formatedWarning(
                "Error occured while parsing current number from a file %s.  Error %s",
                fCurValue.getAbsolutePath(), ex.getMessage());
        try {
          // create backup of a file and
          FileUtils.backupFile(fCurValue);
        } catch (IOException ex1) {
          String msg = String.format(
                  "Error creating backup of a number  file %s.  Error %s",
                  fCurValue.getAbsolutePath(), ex1.getMessage());
          LOG.formatedWarning(msg);
          throw new MEPSException(msg, ex1);
        }
      }
    }

    int iVal = 1;
    if (iCurr != null) {
      iVal = iCurr + 1;
    }

    try {
      Files.write(fCurValue.toPath(), Collections.singleton(iVal + ""),
              StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (IOException ex) {
      String msg = String.format(
              "Error occured write package number to file%s.  Error %s",
              fCurValue.getAbsolutePath(), ex.getMessage());
      LOG.formatedWarning(msg);
      throw new MEPSException(msg, ex);
    }
    return iVal;
  }

  private static File getNumberFile() {
    File pluginRootFolder = new File(SEDSystemProperties.getPluginsFolder(),
            ROOT_FOLDER);
    if (!pluginRootFolder.exists()) {

      pluginRootFolder.mkdirs();

    }
    return  new File(pluginRootFolder, AppConstant.FILE_PACKAGE_NUMBER);
  }

}
