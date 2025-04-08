package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;

@DataObject(generateConverter = true)
public class ApprovalRequest {
    public String getReq_id() {
        return req_id;
    }

    public void setReq_id(String req_id) {
        this.req_id = req_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    String req_id;
    String status;
}
