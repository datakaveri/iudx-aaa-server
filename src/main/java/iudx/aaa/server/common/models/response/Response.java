package iudx.aaa.server.common.models.response;

import io.vertx.core.json.JsonObject;

public class Response {
    private String type;
    private int status;
    private String title;
    private String detail;

    private Response(Builder builder) {
        this.type = builder.type;
        this.status = builder.status;
        this.title = builder.title;
        this.detail = builder.detail;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.put("type", type);
        json.put("status", status);
        json.put("title", title);
        json.put("detail", detail);

        return json;
    }


    public static class Builder {
        private String type;
        private int status;
        private String title;
        private String detail;

        public Builder withUrn(String type) {
            this.type = type;
            return this;
        }

        public Builder withStatus(int status) {
            this.status = status;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withDetail(String detail) {
            this.detail = detail;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
    }


    public String getType() {
        return type;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }


}
