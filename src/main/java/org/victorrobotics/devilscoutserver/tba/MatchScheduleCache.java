package org.victorrobotics.devilscoutserver.tba;

import org.victorrobotics.bluealliance.Endpoint;
import org.victorrobotics.bluealliance.Match;
import org.victorrobotics.bluealliance.Match.Alliance.Color;
import org.victorrobotics.devilscoutserver.cache.Cacheable;
import org.victorrobotics.devilscoutserver.cache.ListValue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchScheduleCache
    extends BlueAllianceCache<String, List<Match>, MatchScheduleCache.MatchSchedule> {
  public enum MatchLevel {
    QUAL("Qualification"),
    QUARTER("Quarterfinal"),
    SEMI("Semifinal"),
    FINAL("Final"),
    UNKNOWN("???");

    private static final MatchLevel[] VALUES = values();

    private final String name;

    MatchLevel(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    static MatchLevel of(Match.Level level) {
      int ordinal = level.ordinal();
      return ordinal >= VALUES.length ? VALUES[VALUES.length - 1] : VALUES[ordinal];
    }
  }

  public static class MatchInfo implements Cacheable<Match> {
    private final String     key;
    private final String     name;
    private final MatchLevel level;
    private final int        set;
    private final int        number;

    private int[]   blue;
    private int[]   red;
    private long    time;
    private boolean completed;

    private Match.ScoreBreakdown redBreakdown;
    private Match.ScoreBreakdown blueBreakdown;

    MatchInfo(Match match) {
      this.key = match.key;
      this.level = MatchLevel.of(match.level);
      this.set = match.setNumber;
      this.number = match.matchNumber;

      String setStr = key.substring(key.lastIndexOf('_') + 1, key.lastIndexOf('m'));
      if (Character.isDigit(setStr.charAt(setStr.length() - 1))) {
        this.name = level + " " + set + "-" + number;
      } else {
        this.name = level + " " + number;
      }

      update(match);
    }

    public boolean update(Match match) {
      if (!Objects.equals(key, match.key)) {
        throw new IllegalArgumentException();
      }

      boolean change = false;

      int[] matchBlueAlliance = parseTeamKeys(match.blueAlliance.teamKeys);
      if (!Arrays.equals(blue, matchBlueAlliance)) {
        blue = matchBlueAlliance;
        change = true;
      }

      int[] matchRedAlliance = parseTeamKeys(match.redAlliance.teamKeys);
      if (!Arrays.equals(red, matchRedAlliance)) {
        red = matchRedAlliance;
        change = true;
      }

      boolean matchIsComplete = match.winningAlliance != Color.NONE;
      if (completed != matchIsComplete) {
        completed = matchIsComplete;
        change = true;
      }

      long matchTime = matchIsComplete ? match.actualTime.getTime() : match.predictedTime.getTime();
      if (time != matchTime) {
        time = matchTime;
        change = true;
      }

      if (!Objects.equals(match.blueScore, blueBreakdown)) {
        blueBreakdown = match.blueScore;
        change = true;
      }

      if (!Objects.equals(match.redScore, redBreakdown)) {
        redBreakdown = match.redScore;
        change = true;
      }

      return change;
    }

    private static int[] parseTeamKeys(List<String> teamKeys) {
      int[] teams = new int[teamKeys.size()];
      for (int i = 0; i < teams.length; i++) {
        teams[i] = Integer.parseInt(teamKeys.get(i)
                                            .substring(3));
      }
      return teams;
    }

    public String getKey() {
      return key;
    }

    public String getName() {
      return name;
    }

    public MatchLevel getLevel() {
      return level;
    }

    public int getSet() {
      return set;
    }

    public int getNumber() {
      return number;
    }

    @SuppressWarnings("java:S2384")
    public int[] getBlue() {
      return blue;
    }

    @SuppressWarnings("java:S2384")
    public int[] getRed() {
      return red;
    }

    public long getTime() {
      return time;
    }

    public boolean isCompleted() {
      return completed;
    }
  }

  public class MatchSchedule extends ListValue<String, List<Match>, Match, MatchInfo> {
    @SuppressWarnings("java:S5867") // Unicode-aware regex
    private static final Pattern MATCH_KEY_PATTERN =
        Pattern.compile("^(20\\d\\d[\\dA-Za-z]{1,8})_(q|ef|qf|sf|f)(\\d*)m(\\d+)$");

    private static final Comparator<String> MATCH_KEY_COMPARATOR = (key1, key2) -> {
      // Skip regex for identical keys
      if (key1.equals(key2)) return 0;

      Matcher matcher1 = MATCH_KEY_PATTERN.matcher(key1);
      Matcher matcher2 = MATCH_KEY_PATTERN.matcher(key2);

      // If we have invalid keys, sort alphabetically
      if (!matcher1.matches() || !matcher2.matches()) {
        return key1.compareTo(key2);
      }

      String eventKey1 = matcher1.group(1);
      String eventKey2 = matcher2.group(1);
      if (!eventKey1.equals(eventKey2)) {
        return eventKey1.compareTo(eventKey2);
      }

      String compLevel1 = matcher1.group(2);
      String compLevel2 = matcher2.group(2);
      if (!compLevel1.equals(compLevel2)) {
        if ("q".equals(compLevel1)) return -1;
        if ("q".equals(compLevel2)) return 1;

        if ("ef".equals(compLevel1)) return -1;
        if ("ef".equals(compLevel2)) return 1;

        if ("qf".equals(compLevel1)) return -1;
        if ("qf".equals(compLevel2)) return 1;

        if ("sf".equals(compLevel1)) return -1;
        if ("sf".equals(compLevel2)) return 1;

        if ("f".equals(compLevel1)) return -1;
        if ("f".equals(compLevel2)) return 1;

        // Unknown level, fail to compare
        return 0;
      }

      String set1 = matcher1.group(3);
      String set2 = matcher2.group(3);
      if (!set1.equals(set2)) {
        // Optional, may be empty
        if (set1.isEmpty()) return -1;
        if (set2.isEmpty()) return 1;

        int setNum1 = Integer.parseInt(set1);
        int setNum2 = Integer.parseInt(set2);
        return Integer.compare(setNum1, setNum2);
      }

      String match1 = matcher1.group(4);
      String match2 = matcher2.group(4);
      if (!match1.equals(match2)) {
        int matchNum1 = Integer.parseInt(match1);
        int matchNum2 = Integer.parseInt(match2);
        return Integer.compare(matchNum1, matchNum2);
      }

      // Keys are equal, but not equal???
      // Should never happen
      return key1.compareTo(key2);
    };

    private final String eventKey;

    public MatchSchedule(String eventKey, List<Match> matches) {
      super(MATCH_KEY_COMPARATOR);
      this.eventKey = eventKey;
      update(matches);
    }

    @Override
    protected MatchInfo createValue(String key, Match data) {
      return new MatchInfo(data);
    }

    @Override
    protected String getKey(Match data) {
      return data.key;
    }

    @Override
    protected List<Match> getList(List<Match> data) {
      return data;
    }

    @Override
    public boolean update(List<Match> data) {
      boolean update = super.update(data);
      if (update) {
        oprs.refresh(eventKey);
      }
      return update;
    }
  }

  private final OprsCache oprs;

  public MatchScheduleCache(OprsCache oprs) {
    this.oprs = oprs;
  }

  @Override
  protected Endpoint<List<Match>> getEndpoint(String eventKey) {
    return Match.endpointForEvent(eventKey);
  }

  @Override
  protected MatchSchedule createValue(String key, List<Match> data) {
    return new MatchSchedule(key, data);
  }
}
