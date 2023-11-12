package org.victorrobotics.devilscoutserver;

import org.victorrobotics.devilscoutserver.controller.LoginController;

import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.OpenApiPluginConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

public class Main {
  @SuppressWarnings("java:S2095") // close Javalin
  public static void main(String... args) {
    Javalin.create(config -> {
      config.http.prefer405over404 = true;

      // @format:off
      config.plugins.register(new OpenApiPlugin(
        new OpenApiPluginConfiguration()
          .withDocumentationPath("/openapi/json")
          .withDefinitionConfiguration((version, definition) -> definition
            .withOpenApiInfo(openApiInfo -> {
              openApiInfo.setTitle("DevilScout Server");
              openApiInfo.setVersion("alpha");
              openApiInfo.setDescription("""
                ## Overview
                Information and statistics on FRC competitions, pooled together by all registered teams.
                ## Authentication
                All endpoints (except for login) require a session key to be passed in the header
                `X-DS-SESSION-KEY`. This will be generated by the server upon successful authentication.
                """);
            })
      )));
      // @format:on

      SwaggerConfiguration uiConfig = new SwaggerConfiguration();
      uiConfig.setDocumentationPath("/openapi/json");
      uiConfig.setUiPath("/openapi/ui");
      config.plugins.register(new SwaggerPlugin(uiConfig));
    })
           .get("/status", ctx -> ctx.result("{\"status\":\"okay\"}"))
           .post("/login", LoginController::login)
           .post("/auth", LoginController::auth)
           .start(80);
  }
}
