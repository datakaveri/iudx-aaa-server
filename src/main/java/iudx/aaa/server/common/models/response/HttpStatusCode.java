package iudx.aaa.server.common.models.response;

public enum HttpStatusCode {

    // 2XX: codes
    NO_CONTENT(204, "No Content", "urn:dx:rs:noContent"),
    SUCCESS(200, "Success", "urn:dx:rs:Success"),

    // 4xx: Client Error
    BAD_REQUEST(400, "Bad Request", "urn:dx:rs:badRequest"),
    NOT_FOUND(404, "Not Found", "urn:dx:rs:notFound"),
    CONFLICT(409, "Conflict", "urn:dx:rs:conflict"),

    // 5xx: Server Error
    INTERNAL_SERVER_ERROR(500, "Internal Server Error", "urn:dx:rs:internalServerError");

    private final int value;
    private final String description;
    private final String urn;

    HttpStatusCode(int value, String description, String urn) {
        this.value = value;
        this.description = description;
        this.urn = urn;
    }

    public static HttpStatusCode getByValue(int value) {
        for (HttpStatusCode status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status code: " + value);
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public String getUrn() {
        return urn;
    }

    @Override
    public String toString() {
        return value + " " + description;
    }
}
