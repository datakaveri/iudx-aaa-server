package iudx.aaa.server.common.models.response;

import java.util.stream.Stream;

public enum ResponseUrn {

    SUCCESS_URN("urn:dx:rs:success", "Success"),
    INVALID_PARAM_URN("urn:dx:rs:invalidParamameter", "Invalid parameter passed"),
    INVALID_PARAM_VALUE_URN("urn:dx:rs:invalidParameterValue", "Invalid parameter value passed"),
    BAD_REQUEST_URN("urn:dx:rs:badRequest", "bad request parameter"),

    BACKING_SERVICE_FORMAT_URN(
      "urn:dx:rs:backend", "format error from backing service [cat,auth etc.]"),
    YET_NOT_IMPLEMENTED_URN("urn:dx:rs:general", "urn yet not implemented in backend verticle.");

    private final String urn;
    private final String message;

    ResponseUrn(String urn, String message) {
        this.urn = urn;
        this.message = message;
    }

    public static ResponseUrn fromCode(final String urn) {
        return Stream.of(values())
                .filter(v -> v.urn.equalsIgnoreCase(urn))
                .findAny()
                .orElse(YET_NOT_IMPLEMENTED_URN); // if backend service dont respond with urn
    }

    public String getUrn() {
        return urn;
    }

    public String getMessage() {
        return message;
    }

    public String toString() {
        return "[" + urn + " : " + message + " ]";
    }
}
