package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;

@DataObject(generateConverter = true)
public class CreateOrganisationRequest {

    public String getOrg_name() {
        return org_name;
    }

    public void setOrg_name(String org_name) {
        this.org_name = org_name;
    }

    public String getDocument_path() {
        return document_path;
    }

    public void setDocument_path(String document_path) {
        this.document_path = document_path;
    }

    String org_name;
    String document_path;

}
