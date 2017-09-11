/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package si.jrc.msh.plugin.meps.enums;

/**
 *
 * @author sluzba
 */
public enum MEPSPayloadPart {
  CONCATENATED_CONTENT("http://laurentius.si/concatenated_content", "application/pdf", "content_%09d.pdf","concatenated_content", "Concatenated PDF content"),
  ENVELOPE_DATA("http://laurentius.si/envelope_data", "text/xml", "envelope_data.xml","envelope_data", "Envelope data"),
  STATUS_REPORT("http://laurentius.si/status_report", "text/xml", "status_report.xml","status_report", "Status report"),
  REMOVE_MAIL("http://laurentius.si/status_report", "text/xml", "remove_mail.xml","remove_mail", "Remove mail request");
  
  
  String type;
  String mimeType;
  String filename;
  String name;
  String desc;
  private MEPSPayloadPart(String type, String mimeType, String filename, String name, String desc){
    this.type = type;
    this.mimeType = mimeType;
    this.filename = filename;
    this.name = name;
    this.desc = desc;
  }

  public String getType() {
    return type;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getFilename() {
    return filename;
  }

  public String getName() {
    return name;
  }

 
  
}
