package iudx.aaa.server.apiserver.dto;

public class RevokeAllRequestDTO {
  private String serial;
  private String fingerprint;

  public String getSerial() {
    return serial;
  }

  public void setSerial(String serial) {
    this.serial = serial;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }


}
