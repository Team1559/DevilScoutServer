package org.victorrobotics.devilscoutserver.controller;

import static org.victorrobotics.devilscoutserver.Utils.base64Encode;

import org.victorrobotics.devilscoutserver.data.QuestionType;
import org.victorrobotics.devilscoutserver.data.UserAccessLevel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiRequired;
import io.javalin.openapi.OpenApiResponse;

public final class QuestionsController extends Controller {
  private static final MatchQuestions     MATCH_QUESTIONS;
  private static final PitQuestions       PIT_QUESTIONS;
  private static final DriveTeamQuestions DRIVE_TEAM_QUESTIONS;

  private static final String MATCH_QUESTIONS_JSON;
  private static final String PIT_QUESTIONS_JSON;
  private static final String DRIVE_TEAM_QUESTIONS_JSON;

  private static final String MATCH_QUESTIONS_HASH;
  private static final String PIT_QUESTIONS_HASH;
  private static final String DRIVE_TEAM_QUESTIONS_HASH;

  static {
    try {
      JsonMapper json = new JsonMapper();
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

      MATCH_QUESTIONS = json.readValue(openResource("/match_questions.json"), MatchQuestions.class);
      PIT_QUESTIONS = json.readValue(openResource("/pit_questions.json"), PitQuestions.class);
      DRIVE_TEAM_QUESTIONS =
          json.readValue(openResource("/drive_team_questions.json"), DriveTeamQuestions.class);

      MATCH_QUESTIONS_JSON = json.writeValueAsString(MATCH_QUESTIONS);
      PIT_QUESTIONS_JSON = json.writeValueAsString(PIT_QUESTIONS);
      DRIVE_TEAM_QUESTIONS_JSON = json.writeValueAsString(DRIVE_TEAM_QUESTIONS);

      MATCH_QUESTIONS_HASH =
          base64Encode(sha256.digest(MATCH_QUESTIONS_JSON.getBytes(StandardCharsets.UTF_8)));
      PIT_QUESTIONS_HASH =
          base64Encode(sha256.digest(PIT_QUESTIONS_JSON.getBytes(StandardCharsets.UTF_8)));
      DRIVE_TEAM_QUESTIONS_HASH =
          base64Encode(sha256.digest(DRIVE_TEAM_QUESTIONS_JSON.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private QuestionsController() {}

  private static InputStream openResource(String name) {
    return QuestionsController.class.getResourceAsStream(name);
  }

  @OpenApi(path = "/questions/match", methods = HttpMethod.GET, tags = "Configuration",
           description = "Get the match scouting questions users should answer.",
           responses = { @OpenApiResponse(status = "200",
                                          content = @OpenApiContent(from = MatchQuestions.class)),
                         @OpenApiResponse(status = "401") })
  public static void matchQuestions(Context ctx) {
    getValidSession(ctx);
    checkIfNoneMatch(ctx, MATCH_QUESTIONS_HASH);

    ctx.json(MATCH_QUESTIONS_JSON);
    setResponseETag(ctx, MATCH_QUESTIONS_HASH);
  }

  @OpenApi(path = "/questions/pit", methods = HttpMethod.GET, tags = "Configuration",
           description = "Get the pit scouting questions users should answer.",
           responses = { @OpenApiResponse(status = "200",
                                          content = @OpenApiContent(from = MatchQuestions.class)),
                         @OpenApiResponse(status = "401") })
  public static void pitQuestions(Context ctx) {
    getValidSession(ctx);
    checkIfNoneMatch(ctx, PIT_QUESTIONS_HASH);

    ctx.json(PIT_QUESTIONS_JSON);
    setResponseETag(ctx, PIT_QUESTIONS_HASH);
  }

  @OpenApi(path = "/questions/drive_team", methods = HttpMethod.GET, tags = "Configuration",
           description = "Get the scouting questions drive teams should answer. Requires ADMIN access.",
           responses = { @OpenApiResponse(status = "200",
                                          content = @OpenApiContent(from = MatchQuestions.class)),
                         @OpenApiResponse(status = "401"), @OpenApiResponse(status = "403") })
  public static void driveTeamQuestions(Context ctx) {
    getValidSession(ctx, UserAccessLevel.ADMIN);
    checkIfNoneMatch(ctx, DRIVE_TEAM_QUESTIONS_HASH);

    ctx.json(DRIVE_TEAM_QUESTIONS_JSON);
    setResponseETag(ctx, DRIVE_TEAM_QUESTIONS_HASH);
  }

  public static record Question(@OpenApiRequired @OpenApiExample("Drivetrain Type") String prompt,
                                @OpenApiRequired QuestionType type,
                                @OpenApiExample("{}") Map<String, Object> config) {}

  public static record MatchQuestions(@OpenApiRequired List<Question> auto,
                                      @OpenApiRequired @OpenApiExample("[]") List<Question> teleop,
                                      @OpenApiRequired @OpenApiExample("[]") List<Question> endgame,
                                      @OpenApiRequired @OpenApiExample("[]") List<Question> general,
                                      @OpenApiRequired
                                      @OpenApiExample("[]") List<Question> human) {}

  public static record PitQuestions(@OpenApiRequired List<Question> specs,
                                    @OpenApiRequired @OpenApiExample("[]") List<Question> auto,
                                    @OpenApiRequired @OpenApiExample("[]") List<Question> teleop,
                                    @OpenApiRequired @OpenApiExample("[]") List<Question> endgame,
                                    @OpenApiRequired
                                    @OpenApiExample("[]") List<Question> general) {}

  public static record DriveTeamQuestions(@OpenApiRequired List<Question> questions) {}
}
