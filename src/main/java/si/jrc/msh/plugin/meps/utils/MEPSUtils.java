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

import java.io.File;
import java.io.FileNotFoundException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.xml.bind.JAXBException;
import si.jrc.msh.plugin.meps.AppConstant;
import si.jrc.msh.plugin.meps.enums.MEPSPartPropertyType;
import si.jrc.msh.plugin.meps.enums.MEPSPayloadPart;
import si.laurentius.commons.SEDValues;
import si.laurentius.commons.enums.MimeValue;
import si.laurentius.commons.exception.FOPException;
import si.laurentius.commons.exception.HashException;
import si.laurentius.commons.exception.SEDSecurityException;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.StorageUtils;
import si.laurentius.commons.utils.Utils;
import si.laurentius.commons.utils.xml.XMLUtils;
import si.laurentius.msh.mail.MSHMailType;
import si.laurentius.msh.outbox.payload.MSHOutPart;
import si.laurentius.msh.outbox.payload.OMPartProperty;
import si.laurentius.plugin.interfaces.exception.TaskException;

/**
 *
 * @author Jože Rihtaršič
 */
public class MEPSUtils {

  protected final SEDLogger LOG = new SEDLogger(MEPSUtils.class);
  
  
  public MSHOutPart createMSHOutPart(MEPSPayloadPart partType,
          Object jaxBObject) throws StorageException, JAXBException, FileNotFoundException {
          File f = StorageUtils.getNewStorageFile(MimeValue.MIME_XML.getSuffix(), partType.getName());
          XMLUtils.serialize(jaxBObject, f);
          return createMSHOutPart(partType, f);
  }

  public MSHOutPart createMSHOutPart(MEPSPayloadPart partType,
          File payloadPartFile) throws StorageException
           {
    long l = LOG.logStart();
    
    

    MSHOutPart ptNew = new MSHOutPart();
    ptNew.setSource(AppConstant.PLUGIN_TYPE);
    ptNew.setEncoding(SEDValues.ENCODING_BASE64);
    ptNew.setIsSent(Boolean.TRUE);
    ptNew.setIsReceived(Boolean.FALSE);
    ptNew.setMimeType(partType.getMimeType());
    ptNew.setName(partType.getName());
    ptNew.setType(partType.getType());
    ptNew.setDescription(partType.getName());
    ptNew.setFilepath(StorageUtils.getRelativePath(payloadPartFile));
    ptNew.setFilename(payloadPartFile.getName());

    ptNew.setIsEncrypted(Boolean.FALSE);

    addOrUpdatePartProperty(ptNew, MEPSPartPropertyType.PartCreated.getType(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

    LOG.logEnd(l, partType);

    return ptNew;
  }

  public void createFolderIfNotExist(File folder) throws TaskException {
    if (!folder.exists() && !folder.mkdirs()) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Error creating folder: '" + folder.getAbsolutePath() + "' not exists ");
    }

    if (!folder.isDirectory()) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              folder.getAbsolutePath() + " is not folder!");
    }
  }

  public String getPartProperty(MSHOutPart op, String propertytype) {
    if (op == null || op.getOMPartProperties().isEmpty()) {
      return null;
    }
    for (OMPartProperty p : op.getOMPartProperties()) {
      if (Objects.equals(p.getName(), propertytype)) {
        return p.getValue();
      }
    }
    return null;
  }

  public void addOrUpdatePartProperty(MSHOutPart op, String propertytype,
          String value) {
    if (op == null) {
      return;
    }
    // !property with null value must not exists
    // check is exits    
    for (OMPartProperty p : op.getOMPartProperties()) {
      if (Objects.equals(p.getName(), propertytype)) {
        if (Utils.isEmptyString(value)) {
          op.getOMPartProperties().remove(p);
          return;
        } else {
          p.setValue(value);
          return;
        }
      }
    }
    // and new property
    if (!Utils.isEmptyString(value)) {
      OMPartProperty prp = new OMPartProperty();
      prp.setName(propertytype);
      prp.setValue(value);
      op.getOMPartProperties().add(prp);
    }
  }

}
