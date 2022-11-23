package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.NIL_UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.User;

public class CreatePolicyBar {

  User policySetter;
  UUID delegatorId = UUID.fromString(NIL_UUID);
  List<CreatePolicyContext> context;

  public User getPolicySetter() {
    return policySetter;
  }

  public void setPolicySetter(User policySetter) {
    this.policySetter = policySetter;
  }

  public UUID getDelegatorId() {
    return delegatorId;
  }

  public void setDelegatorId(UUID delegatorId) {
    this.delegatorId = delegatorId;
  }

  public List<CreatePolicyContext> getContext() {
    return context;
  }

  public void setContext(List<CreatePolicyContext> context) {
    this.context = context;
  }
  
  public boolean isDelegated()
  {
    return !delegatorId.equals(UUID.fromString(NIL_UUID));
  }

  public CreatePolicyBar(User policySetter, JsonObject delegateData,
      List<CreatePolicyRequest> request) {
    this.policySetter = policySetter;
    if (!delegateData.isEmpty()) {
      this.delegatorId = UUID.fromString(delegateData.getString("providerId"));
    }
    
    this.context = new ArrayList<CreatePolicyContext>();
    request.forEach(req -> {
      this.context.add(new CreatePolicyContext(req));
    });
  }
}
