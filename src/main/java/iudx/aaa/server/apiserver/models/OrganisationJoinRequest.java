package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;

@DataObject(generateConverter = true)
public class OrganisationJoinRequest {

    public String getOrg_id() {
        return org_id;
    }

    public void setOrg_id(String org_id) {
        this.org_id = org_id;
    }

    String org_id;
}
