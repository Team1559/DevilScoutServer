package org.victorrobotics.devilscoutserver.controller;

import org.victorrobotics.devilscoutserver.cache.MatchScheduleCache;
import org.victorrobotics.devilscoutserver.data.Session;
import org.victorrobotics.devilscoutserver.database.SessionDB;
import org.victorrobotics.devilscoutserver.database.TeamConfigDB;
import org.victorrobotics.devilscoutserver.database.UserDB;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

public class Controller {
  public static final String SESSION_HEADER = "X-DS-SESSION-KEY";

  private static UserDB       USERS;
  private static SessionDB    SESSIONS;
  private static TeamConfigDB TEAMS;

  private static MatchScheduleCache MATCH_SCHEDULES;

  protected Controller() {}

  @SuppressWarnings("java:S2221") // catch generic exception
  protected static <T> T jsonDecode(Context ctx, Class<T> clazz) {
    try {
      return ctx.bodyAsClass(clazz);
    } catch (Exception e) {
      throw new BadRequestResponse();
    }
  }

  public static void setUserDB(UserDB users) {
    USERS = users;
  }

  public static void setSessionDB(SessionDB sessions) {
    SESSIONS = sessions;
  }

  public static void setTeamDB(TeamConfigDB teams) {
    TEAMS = teams;
  }

  public static void setMatchSchedules(MatchScheduleCache schedules) {
    MATCH_SCHEDULES = schedules;
  }

  protected static UserDB userDB() {
    return USERS;
  }

  protected static SessionDB sessionDB() {
    return SESSIONS;
  }

  protected static TeamConfigDB teamDB() {
    return TEAMS;
  }

  protected static MatchScheduleCache matchSchedules() {
    return MATCH_SCHEDULES;
  }

  protected static Session getValidSession(Context ctx) {
    String sessionID = ctx.header(SESSION_HEADER);
    if (sessionID == null) {
      throw new UnauthorizedResponse();
    }

    Session session = SESSIONS.getSession(sessionID);
    if (session == null || session.isExpired()) {
      throw new UnauthorizedResponse();
    }

    return session;
  }
}
