package iudx.aaa.server.common.models.response;

import java.util.stream.Stream;

public enum ResponseType {
    Ok(200, "Ok"),
    Created(201, "created"),
    NoContent(204, "Already Exist");

    private final int code;
    private final String message;

    ResponseType(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ResponseType fromCode(final int code) {
        return Stream.of(values())
                .filter(v -> v.code == code)
                .findAny()
                .orElse(null);
    }

    public String toString() {
        return "[" + code + " : " + message + " ]";
    }
}
