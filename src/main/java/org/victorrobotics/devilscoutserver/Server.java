package org.victorrobotics.devilscoutserver;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.patch;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

import org.victorrobotics.bluealliance.Endpoint;
import org.victorrobotics.devilscoutserver.analysis.Analyzer;
import org.victorrobotics.devilscoutserver.analysis.CrescendoAnalyzer;
import org.victorrobotics.devilscoutserver.analysis.TeamAnalysisCache;
import org.victorrobotics.devilscoutserver.cache.Cache;
import org.victorrobotics.devilscoutserver.controller.AnalysisController;
import org.victorrobotics.devilscoutserver.controller.Controller;
import org.victorrobotics.devilscoutserver.controller.Controller.Session;
import org.victorrobotics.devilscoutserver.controller.EventController;
import org.victorrobotics.devilscoutserver.controller.QuestionController;
import org.victorrobotics.devilscoutserver.controller.SessionController;
import org.victorrobotics.devilscoutserver.controller.SubmissionController;
import org.victorrobotics.devilscoutserver.controller.TeamController;
import org.victorrobotics.devilscoutserver.controller.UserController;
import org.victorrobotics.devilscoutserver.database.Database;
import org.victorrobotics.devilscoutserver.database.EntryDatabase;
import org.victorrobotics.devilscoutserver.database.TeamDatabase;
import org.victorrobotics.devilscoutserver.database.UserDatabase;
import org.victorrobotics.devilscoutserver.tba.EventCache;
import org.victorrobotics.devilscoutserver.tba.EventTeamCache;
import org.victorrobotics.devilscoutserver.tba.EventTeamListCache;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.javalin.Javalin;
import io.javalin.http.Handler;
import io.javalin.http.HttpResponseException;
import io.javalin.openapi.ApiKeyAuth;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.OpenApiPluginConfiguration;
import io.javalin.openapi.plugin.SecurityComponentConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {
  private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

  private static final Handler UNIMPLEMENTED = ctx -> ctx.result("UNIMPLEMENTED");

  private static final String API_DESCRIPTION =
      """
          ## Overview
          Information and statistics on FRC competitions, pooled together by all registered teams.
          ## Authentication
          All endpoints (except for `/login` and `/auth`) require a session key to be passed in the header
          `%s`. This will be generated by the server upon successful authentication.
          """.formatted(Controller.SESSION_HEADER);
  // Hack to inject directly instead of serving file
  private static final String REMOVE_TOP_BAR = """
      '></script><script>
        /* INJECTED */
        let onLoad = window.onload;
        window.onload = function() {
          onLoad();
          let topbar = document.getElementsByClassName('topbar')[0];
          topbar.parentNode.removeChild(topbar);
        };
      </script><script src='""";

  private final Javalin javalin;

  public Server() {
    javalin = Javalin.create(config -> {
      config.http.prefer405over404 = true;

      // @format:off
      config.plugins.register(new OpenApiPlugin(
          new OpenApiPluginConfiguration()
              .withDocumentationPath("/openapi/json")
              .withDefinitionConfiguration((version, definition) -> definition
                  .withOpenApiInfo(openApiInfo -> {
                    openApiInfo.setTitle("DevilScout Server");
                    openApiInfo.setVersion("alpha");
                    openApiInfo.setDescription(API_DESCRIPTION);
                  })
                  .withSecurity(new SecurityComponentConfiguration()
                      .withSecurityScheme("Session", new ApiKeyAuth() {
                        @Override
                        public String getName() {
                          return Controller.SESSION_HEADER;
                        }
                      })))));
      // @format:on

      SwaggerConfiguration uiConfig = new SwaggerConfiguration();
      uiConfig.setTitle("DevilScout Server API");
      uiConfig.setDocumentationPath("/openapi/json");
      uiConfig.setUiPath("/openapi/ui");
      uiConfig.injectJavaScript(REMOVE_TOP_BAR);
      config.plugins.register(new SwaggerPlugin(uiConfig));
    });

    javalin.routes(() -> {
      post("login", SessionController::login);
      post("auth", SessionController::auth);
      delete("logout", SessionController::logout);

      get("sessions/{session_id}", SessionController::getSession);

      path("events", () -> {
        get(EventController::getAllEvents);

        path("{event}", () -> {
          get(EventController::getEvent);
          get("teams", EventController::getTeams);
          get("match-schedule", EventController::getMatchSchedule);
        });
      });

      path("questions", () -> {
        get("match", QuestionController::matchQuestions);
        get("pit", QuestionController::pitQuestions);
        get("drive-team", QuestionController::driveTeamQuestions);
      });

      path("submissions", () -> {
        post("match-scouting", SubmissionController::submitMatchScouting);
        post("pit-scouting", SubmissionController::submitPitScouting);
        post("drive-team-scouting", SubmissionController::submitDriveTeamScouting);
      });

      path("analysis", () -> {
        get("teams", AnalysisController::teams);
        post("simulation", UNIMPLEMENTED); // request match simulation
        post("optimization", UNIMPLEMENTED); // request alliance optimization
      });

      path("teams/{team}", () -> {
        get(TeamController::getTeam);
        patch(TeamController::editTeam);

        path("users", () -> {
          get(TeamController::usersOnTeam);
          post(UserController::registerUser);

          path("{id}", () -> {
            get(UserController::getUser);
            delete(UserController::deleteUser);
            patch(UserController::editUser);
          });
        });
      });
    });

    javalin.exception(HttpResponseException.class, (e, ctx) -> {
      int status = e.getStatus();
      ctx.status(status);
      if (status >= 400) {
        ctx.json(new Controller.Error(e.getMessage()));
      }
    });
    javalin.exception(Exception.class, (e, ctx) -> {
      ctx.status(500);
      ctx.json(new Controller.Error(e.getMessage()));
      LOGGER.warn("{} occurred while executing request {} {} : {}", e.getClass()
                                                                     .getSimpleName(),
                  ctx.method(), ctx.fullUrl(), e.getMessage());
    });
  }

  public void start() {
    javalin.start(8000);
  }

  public void stop() {
    javalin.stop();
  }

  @SuppressWarnings("java:S2095") // close the executor
  public static void main(String... args) {
    LOGGER.info("Connecting to database...");
    Database.initConnectionPool();
    Controller.setUserDB(new UserDatabase());
    Controller.setTeamDB(new TeamDatabase());
    Controller.setMatchEntryDB(new EntryDatabase("match_entries", true));
    Controller.setPitEntryDB(new EntryDatabase("pit_entries", false));
    Controller.setDriveTeamEntryDB(new EntryDatabase("drive_team_entries", true));
    LOGGER.info("Database connected");

    LOGGER.info("Initializing memory caches...");
    Controller.setEventCache(new EventCache());
    Controller.setTeamCache(new EventTeamCache());
    Controller.setEventTeamsCache(new EventTeamListCache(Controller.teamCache()));
    Controller.setMatchScheduleCache(new MatchScheduleCache());
    LOGGER.info("Memory caches ready");

    LOGGER.info("Initializing analysis...");
    Analyzer analyzer = new CrescendoAnalyzer(Controller.matchEntryDB(), Controller.pitEntryDB(),
                                              Controller.driveTeamEntryDB());
    Controller.setTeamAnalysisCache(new TeamAnalysisCache(analyzer));
    LOGGER.info("Analysis ready");

    LOGGER.info("Starting daemon services...");
    ThreadFactory blueAllianceThreads = Thread.ofVirtual()
                                              .name("BlueAlliance-", 0)
                                              .factory();
    Endpoint.setExecutor(Executors.newFixedThreadPool(16, blueAllianceThreads));

    ThreadFactory refreshThreads = Thread.ofPlatform()
                                         .name("Refresh-", 0)
                                         .factory();
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, refreshThreads);
    executor.scheduleAtFixedRate(() -> refreshCache(Controller.eventCache()), 0, 5,
                                 TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(() -> refreshCache(Controller.matchScheduleCache()), 0, 1,
                                 TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(() -> {
      refreshCache(Controller.teamCache());
      refreshCache(Controller.eventTeamsCache());
    }, 0, 5, TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(() -> {
      ConcurrentMap<String, Session> sessions = Controller.sessions();
      long start = System.currentTimeMillis();
      int size = sessions.size();
      sessions.values()
              .removeIf(Session::isExpired);
      LOGGER.info("Purged {} expired sessions in {}ms", size - sessions.size(),
                  System.currentTimeMillis() - start);
    }, 0, 5, TimeUnit.MINUTES);
    executor.scheduleAtFixedRate(() -> refreshCache(Controller.teamAnalysisCache()), 0, 15,
                                 TimeUnit.MINUTES);
    LOGGER.info("Daemon services running");

    LOGGER.info("Starting HTTP server...");
    Server server = new Server();
    server.start();
    LOGGER.info("HTTP server started");

    LOGGER.info("DevilScoutServer startup complete, main thread exiting");
  }

  private static void refreshCache(Cache<?, ?, ?> cache) {
    long start = System.currentTimeMillis();
    try {
      cache.refresh();
    } catch (Exception e) {
      LOGGER.info("Cache refresh: ", e);
    }
    LOGGER.info("Refreshed {} ({}) in {}ms", cache.getClass()
                                                  .getSimpleName(),
                cache.size(), System.currentTimeMillis() - start);
  }
}
