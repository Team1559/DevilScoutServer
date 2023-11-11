package org.victorrobotics.devilscoutserver.auth;

import org.victorrobotics.devilscoutserver.RequestHandler;
import org.victorrobotics.devilscoutserver.database.CredentialDB;
import org.victorrobotics.devilscoutserver.database.Credentials;
import org.victorrobotics.devilscoutserver.database.Session;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;

@OpenApi(path = "/auth", methods = HttpMethod.POST, tags = "Login",
         description = "Authenticates a client. Must have already called `/login` to compute clientProof.",
         requestBody = @OpenApiRequestBody(required = true,
                                           content = @OpenApiContent(from = SCRAM_AuthHandler.AuthRequest.class)),
         responses = { @OpenApiResponse(status = "200", description = "Authentication successful"),
                       @OpenApiResponse(status = "400", description = "Bad request"),
                       @OpenApiResponse(status = "401", description = "Invalid credentials"),
                       @OpenApiResponse(status = "404", description = "Unknown user") })
public class SCRAM_AuthHandler extends RequestHandler {
  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9\\s]{1,32}");

  private final CredentialDB database;
  private final SecureRandom random;

  public SCRAM_AuthHandler(CredentialDB database) {
    this.database = database;
    this.random = new SecureRandom();
  }

  @Override
  public void handle(Context ctx) throws Exception {
    InputStream requestStream = ctx.bodyInputStream();
    String requestBody = new String(requestStream.readNBytes(192));
    if (requestStream.read() != -1) {
      ctx.status(413);
      return;
    }
    requestStream.close();

    AuthRequest request = parse(requestBody);
    if (request == null) {
      ctx.status(400);
      return;
    }

    Credentials credentials = database.get(request.team, request.name);
    if (credentials == null) {
      ctx.status(404);
      return;
    }

    MessageDigest hashFunction;
    Mac hmacFunction;
    try {
      hashFunction = MessageDigest.getInstance(HASH_ALGORITHM);
      hmacFunction = Mac.getInstance(HMAC_ALGORITHM);
      hmacFunction.init(new SecretKeySpec(credentials.storedKey(), HMAC_ALGORITHM));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }

    String user = request.team + request.name;
    byte[] userHash = hashFunction.digest(user.getBytes());
    byte[] nonceHash = hashFunction.digest(request.nonce);
    if (!database.containsNonce(userHash, nonceHash)) {
      ctx.status(401);
      return;
    }

    byte[] userAndNonce = toStr(request.team + request.name, request.nonce);
    byte[] clientSignature = hmacFunction.doFinal(userAndNonce);
    byte[] clientKey = xor(request.clientProof, clientSignature);
    byte[] storedKey = hashFunction.digest(clientKey);
    if (!Arrays.equals(storedKey, credentials.storedKey())) {
      ctx.status(401);
      return;
    }

    try {
      hmacFunction.init(new SecretKeySpec(credentials.serverKey(), HMAC_ALGORITHM));
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
    byte[] serverSignature = hmacFunction.doFinal(userAndNonce);

    Session session = generateSession(credentials);
    String response = "v=" + formatHex(serverSignature) + ",i=" + formatHex(session.sessionID)
        + ",p=" + credentials.permissions()
                             .toString()
                             .toLowerCase()
        + ",n=Xander Bhalla";
    ctx.result(base64Encode(response));
  }

  private Session generateSession(Credentials credentials) {
    long sessionID = random.nextLong();
    return new Session(sessionID, credentials.userID(), credentials.permissions());
  }

  private static AuthRequest parse(String requestBody) {
    // request format: "t={team},n={name},r={nonce},p={clientProof}"
    try {
      String request = base64Decode(requestBody);
      if (!request.startsWith("t=")) return null;
      int commaIndex = request.indexOf(',');
      if (commaIndex < 3 || commaIndex > 6) return null;

      int team = Integer.parseInt(request.substring(2, commaIndex));
      if (team < 0 || team > 9999) return null;

      request = request.substring(commaIndex + 1);
      if (!request.startsWith("n=")) return null;
      commaIndex = request.indexOf(',');
      if (commaIndex < 3 || commaIndex > 34) return null;

      String name = request.substring(2, commaIndex);
      if (!VALID_NAME.matcher(name)
                     .matches()) {
        return null;
      }

      request = request.substring(commaIndex + 1);
      commaIndex = request.indexOf(',');
      if (!request.startsWith("r=") || commaIndex != 34) return null;

      byte[] nonce = parseHex(request.substring(2, 34));

      request = request.substring(35);
      if (!request.startsWith("p=") || request.length() != 66) return null;

      byte[] clientProof = parseHex(request.substring(2));

      return new AuthRequest(team, name, nonce, clientProof);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static byte[] xor(byte[] bytes1, byte[] bytes2) {
    assert bytes1.length == bytes2.length;
    byte[] bytes = new byte[bytes1.length];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) (bytes1[i] ^ bytes2[i]);
    }
    return bytes;
  }

  private static byte[] toStr(String username, byte[] nonce) {
    byte[] bytes = new byte[username.length() + nonce.length];
    byte[] userBytes = username.getBytes();
    System.arraycopy(userBytes, 0, bytes, 0, userBytes.length);
    System.arraycopy(nonce, 0, bytes, userBytes.length, nonce.length);
    return bytes;
  }

  static record AuthRequest(@OpenApiExample("1559") int team,
                            @OpenApiExample("xander") String name,
                            @OpenApiExample("EjRWeJCrze8SNFZ4kKvN7w==") byte[] nonce,
                            @OpenApiExample("EjRWeJCrze8SNFZ4kKvN7xI0VniQq83vEjRWeJCrze8=") byte[] clientProof) {}
}
