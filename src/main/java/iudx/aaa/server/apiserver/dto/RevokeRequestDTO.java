package iudx.aaa.server.apiserver.dto;

import java.util.List;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RevokeRequestDTO {
  
  @JsonProperty("token-hashes")
  @NotNull
  private List<String > tokenHashes;

  public List<String> getTokenHashes() {
    return tokenHashes;
  }

  public void setTokenHashes(List<String> tokenHashes) {
    this.tokenHashes = tokenHashes;
  }

  @Override
  public String toString() {
    return "RevokeRequest [tokenHashes=" + tokenHashes + "]";
  }
  
}
