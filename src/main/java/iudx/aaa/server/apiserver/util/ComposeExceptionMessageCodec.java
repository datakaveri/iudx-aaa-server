package iudx.aaa.server.apiserver.util;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

/**
 * Message codec for {@link iudx.aaa.server.apiserver.util.ComposeException}. This is used to decode
 * and encode ComposeException accross the event bus. This is especially necessary for clustered
 * vert.x. {@link iudx.aaa.server.deploy.Deployer} and {@link iudx.aaa.server.DeployerDev} add this
 * message codec to the vertx object.
 */
public class ComposeExceptionMessageCodec
    implements MessageCodec<ComposeException, ComposeException> {

  @Override
  public void encodeToWire(Buffer buffer, ComposeException customMessage) {
    // Easiest ways is using JSON object
    JsonObject jsonToEncode = customMessage.getResponse().toJson();

    // Encode object to string
    String jsonToStr = jsonToEncode.encode();

    // Length of JSON: is NOT characters count
    int length = jsonToStr.getBytes().length;

    // Write data into given buffer
    buffer.appendInt(length);
    buffer.appendString(jsonToStr);
  }

  @Override
  public ComposeException decodeFromWire(int position, Buffer buffer) {
    // My custom message starting from this *position* of buffer
    int _pos = position;

    // Length of JSON
    int length = buffer.getInt(_pos);

    // Get JSON string by it`s length
    // Jump 4 because getInt() == 4 bytes
    String jsonStr = buffer.getString(_pos += 4, _pos += length);
    JsonObject contentJson = new JsonObject(jsonStr);

    // We can finally create custom message object
    /* TODO: change this so that we use Response.fromJson() or something */
    return new ComposeException(
        contentJson.getInteger("status"),
        contentJson.getString("type"),
        contentJson.getString("title"),
        contentJson.getString("detail"));
  }

  @Override
  public ComposeException transform(ComposeException customMessage) {
    // If a message is sent *locally* across the event bus.
    // This example sends message just as is
    return customMessage;
  }

  @Override
  public String name() {
    // Each codec must have a unique name.
    // This is used to identify a codec when sending a message and for unregistering codecs.
    return this.getClass().getSimpleName();
  }

  @Override
  public byte systemCodecID() {
    // Always -1
    return -1;
  }
}
