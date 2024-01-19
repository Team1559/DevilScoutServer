package org.victorrobotics.devilscoutserver.controller;

import org.victorrobotics.devilscoutserver.database.User;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.SQLException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

public final class UserController extends Controller {
  private UserController() {}

  @OpenApi(path = "/teams/{team}/users", methods = HttpMethod.POST, tags = "Teams",
           pathParams = @OpenApiParam(name = "team", type = Integer.class, required = true),
           summary = "ADMIN",
           description = "Register a new user. Requires ADMIN, and new user must be on the same team.",
           security = @OpenApiSecurity(name = "Session"),
           requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UserRegistration.class)),
           responses = { @OpenApiResponse(status = "201",
                                          content = @OpenApiContent(from = User.class)),
                         @OpenApiResponse(status = "400",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "401",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "403",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "404",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "409",
                                          content = @OpenApiContent(from = Error.class)) })
  public static void registerUser(Context ctx) throws SQLException {
    Session session = getValidSession(ctx);
    session.verifyAdmin();

    UserRegistration registration = jsonDecode(ctx, UserRegistration.class);
    int team = registration.team();

    if (session.getTeam() != team) {
      throwForbidden();
      return;
    }

    if (!teamDB().containsTeam(team)) {
      throwTeamNotFound(team);
      return;
    }

    String username = registration.username();
    if (userDB().getUser(team, username) != null) {
      throw new ConflictResponse("User " + username + "@" + team + " already exists");
    }

    byte[][] auth = computeAuthentication(registration.password());
    byte[] salt = auth[0];
    byte[] storedKey = auth[1];
    byte[] serverKey = auth[2];

    User user = userDB().registerUser(team, username, registration.fullName(), registration.admin(),
                                      salt, storedKey, serverKey);
    ctx.json(user);
    throwCreated();
  }

  @OpenApi(path = "/teams/{team}/users/{id}", methods = HttpMethod.GET, tags = "Teams",
           pathParams = { @OpenApiParam(name = "team", type = Integer.class, required = true),
                          @OpenApiParam(name = "id", type = String.class, required = true) },
           summary = "ADMIN?",
           description = "Get user information. All users may access themselves. "
               + "Accessing other users on the same team requires ADMIN."
               + "Accessing users from other teams is forbidden.",
           security = @OpenApiSecurity(name = "Session"),
           responses = { @OpenApiResponse(status = "200",
                                          content = @OpenApiContent(from = User.class)),
                         @OpenApiResponse(status = "400",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "401",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "403",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "404",
                                          content = @OpenApiContent(from = Error.class)) })
  @SuppressWarnings("java:S1941") // move session closer to code that uses it
  public static void getUser(Context ctx) throws SQLException {
    Session session = getValidSession(ctx);
    session.verifyAdmin();

    String userId = ctx.pathParam("id");
    if (!userId.equals(session.getUser())) {
      session.verifyAdmin();
    }

    User user = userDB().getUser(userId);
    if (user == null) {
      throwUserNotFound(userId);
      return;
    }

    if (user.team() != session.getTeam()) {
      throwForbidden();
      return;
    }

    ctx.json(user);
  }

  @OpenApi(path = "/teams/{team}/users/{id}", methods = HttpMethod.PATCH, tags = "Teams",
           summary = "ADMIN?",
           description = "Edit a user's information. All users may edit themselves. "
               + "Editing other users on the same team requires ADMIN. "
               + "If changing the user's admin status, the client must be an admin.",
           pathParams = { @OpenApiParam(name = "team", type = Integer.class, required = true),
                          @OpenApiParam(name = "id", type = String.class, required = true) },
           security = @OpenApiSecurity(name = "Session"),
           requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UserEdits.class)),
           responses = { @OpenApiResponse(status = "200",
                                          content = @OpenApiContent(from = User.class)),
                         @OpenApiResponse(status = "400",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "401",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "403",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "409",
                                          content = @OpenApiContent(from = Error.class)) })
  @SuppressWarnings("java:S1941") // move session closer to code that uses it
  public static void editUser(Context ctx) throws SQLException {
    Session session = getValidSession(ctx);

    String userId = ctx.pathParam("id");
    if (!userId.equals(session.getUser())) {
      session.verifyAdmin();
    }

    User user = userDB().getUser(userId);
    if (user == null) {
      throwUserNotFound(userId);
      return;
    }

    if (session.getTeam() != user.team()) {
      throwForbidden();
      return;
    }

    UserEdits edits = jsonDecode(ctx, UserEdits.class);
    if (edits.admin() != null) {
      session.verifyAdmin();
    }

    byte[][] authInfo = null;
    if (edits.password() != null) {
      authInfo = computeAuthentication(edits.password());
    }

    user = userDB().editUser(userId, edits.username(), edits.fullName(), edits.admin(), authInfo);

    ctx.json(user);
  }

  @OpenApi(path = "/teams/{team}/users/{id}", methods = HttpMethod.DELETE, tags = "Teams",
           summary = "ADMIN?",
           pathParams = { @OpenApiParam(name = "team", type = Integer.class, required = true),
                          @OpenApiParam(name = "id", type = String.class, required = true) },
           description = "Delete a user. All users may delete themselves. "
               + "Deleting another user on your team requires ADMIN. Deleting users on other teams is forbidden.",
           security = @OpenApiSecurity(name = "Session"),
           responses = { @OpenApiResponse(status = "204"),
                         @OpenApiResponse(status = "400",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "401",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "403",
                                          content = @OpenApiContent(from = Error.class)),
                         @OpenApiResponse(status = "404",
                                          content = @OpenApiContent(from = Error.class)) })
  @SuppressWarnings("java:S1941") // move session closer to code that uses it
  public static void deleteUser(Context ctx) throws SQLException {
    Session session = getValidSession(ctx);

    String userId = ctx.pathParam("id");
    if (!userId.equals(session.getUser())) {
      session.verifyAdmin();
    }

    User user = userDB().getUser(userId);
    if (user == null) {
      throwUserNotFound(userId);
      return;
    }

    if (session.getTeam() != user.team()) {
      throwForbidden();
      return;
    }

    userDB().deleteUser(user.id());
    throwNoContent();
  }

  private static byte[][] computeAuthentication(String password) {
    try {
      byte[] salt = new byte[8];
      SECURE_RANDOM.nextBytes(salt);

      SecretKeyFactory factory = SecretKeyFactory.getInstance(KEYGEN_ALGORITHM);
      KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 4096, 256);
      SecretKey saltedPassword = factory.generateSecret(keySpec);

      MessageDigest sha256 = MessageDigest.getInstance(HASH_ALGORITHM);
      Mac hmacSha256 = Mac.getInstance(MAC_ALGORITHM);
      hmacSha256.init(saltedPassword);

      byte[] clientKey = hmacSha256.doFinal("Client Key".getBytes());
      byte[] storedKey = sha256.digest(clientKey);
      byte[] serverKey = hmacSha256.doFinal("Server Key".getBytes());
      return new byte[][] { salt, storedKey, serverKey };
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  static record UserRegistration(@OpenApiRequired @OpenApiExample("1559")
  @JsonProperty(required = true) int team,
                                 @OpenApiRequired @OpenApiExample("xander")
                                 @JsonProperty(required = true) String username,
                                 @OpenApiRequired @OpenApiExample("Xander Bhalla")
                                 @JsonProperty(required = true) String fullName,
                                 @OpenApiRequired @JsonProperty(required = true) boolean admin,
                                 @OpenApiRequired @OpenApiExample("password")
                                 @JsonProperty(required = true) String password) {}

  static record UserEdits(@OpenApiExample("xander") String username,
                          @OpenApiExample("Xander Bhalla") String fullName,
                          @OpenApiExample("false") Boolean admin,
                          @OpenApiExample("password") String password) {}
}
