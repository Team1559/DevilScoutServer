package org.victorrobotics.devilscoutserver.analysis;

import org.victorrobotics.bluealliance.Match.Alliance;
import org.victorrobotics.bluealliance.ScoreBreakdown;
import org.victorrobotics.devilscoutserver.analysis.data.NumberSummary;
import org.victorrobotics.devilscoutserver.analysis.statistics.StatisticsPage;
import org.victorrobotics.devilscoutserver.database.DataEntry;
import org.victorrobotics.devilscoutserver.database.EntryDatabase;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache.MatchInfo;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache.MatchSchedule;
import org.victorrobotics.devilscoutserver.tba.OprsCache;
import org.victorrobotics.devilscoutserver.tba.OprsCache.TeamOpr;
import org.victorrobotics.devilscoutserver.tba.RankingsCache;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class Analyzer<B extends ScoreBreakdown, D> {
  private final EntryDatabase matchEntryDB;
  private final EntryDatabase pitEntryDB;
  private final EntryDatabase driveTeamEntryDB;

  private final MatchScheduleCache matchScheduleCache;
  private final OprsCache          oprsCache;
  private final RankingsCache      rankingsCache;

  protected Analyzer(EntryDatabase matchEntryDB, EntryDatabase pitEntryDB,
                     EntryDatabase driveTeamEntryDB, MatchScheduleCache matchScheduleCache,
                     OprsCache teamOprsCache, RankingsCache rankingsCache) {
    this.matchEntryDB = matchEntryDB;
    this.pitEntryDB = pitEntryDB;
    this.driveTeamEntryDB = driveTeamEntryDB;
    this.matchScheduleCache = matchScheduleCache;
    this.oprsCache = teamOprsCache;
    this.rankingsCache = rankingsCache;
  }

  protected abstract boolean isValidMatchEntry(DataEntry matchEntry,
                                               TeamScoreBreakdown<B> breakdown);

  protected abstract D computeData(Data inputs);

  protected abstract List<StatisticsPage> generateStatistics(D data);

  public D computeData(String eventKey, int team) {
    return computeData(new Data(eventKey, team));
  }

  protected class Data {
    private final Map<String, List<DataEntry>> matchEntries;
    private final Map<String, List<DataEntry>> driveTeamEntries;

    private final List<DataEntry> pitEntries;

    private final Map<String, TeamScoreBreakdown<B>> scoreBreakdowns;

    private final TeamOpr            opr;
    private final RankingsCache.Team rankings;

    Data(String eventKey, int team) {
      scoreBreakdowns = new LinkedHashMap<>();
      MatchSchedule schedule = matchScheduleCache.get(eventKey)
                                                 .value();
      for (MatchInfo match : schedule.values()) {
        TeamScoreBreakdown<B> breakdown = resolveBreakdown(match, team);
        if (breakdown != null) {
          scoreBreakdowns.put(match.getKey(), breakdown);
        }
      }

      try {
        matchEntries = new LinkedHashMap<>();
        List<DataEntry> matchEntriesList = matchEntryDB.getEntries(eventKey, team);
        for (DataEntry entry : matchEntriesList) {
          TeamScoreBreakdown<B> breakdown = scoreBreakdowns.get(entry.matchKey());
          if (breakdown == null || isValidMatchEntry(entry, breakdown)) {
            matchEntries.computeIfAbsent(entry.matchKey(), k -> new ArrayList<>(1))
                        .add(entry);
          }
        }

        driveTeamEntries = new LinkedHashMap<>();
        List<DataEntry> driveTeamEntriesList = driveTeamEntryDB.getEntries(eventKey, team);
        for (DataEntry entry : driveTeamEntriesList) {
          driveTeamEntries.computeIfAbsent(entry.matchKey(), k -> new ArrayList<>(1))
                          .add(entry);
        }

        pitEntries = pitEntryDB.getEntries(eventKey, team);
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }

      opr = oprsCache.get(eventKey)
                     .value()
                     .get(team);

      rankings = rankingsCache.get(eventKey)
                              .value()
                              .get(team);
    }

    @SuppressWarnings("unchecked") // breakdowns always from current year
    private TeamScoreBreakdown<B> resolveBreakdown(MatchInfo match, int team) {
      if (match.getRedBreakdown() != null) {
        int[] redAlliance = match.getRed();
        for (int i = 0; i < redAlliance.length; i++) {
          if (redAlliance[i] == team) {
            return new TeamScoreBreakdown<>(match, (B) match.getRedBreakdown(), Alliance.Color.RED,
                                            i);
          }
        }
      }

      if (match.getBlueBreakdown() != null) {
        int[] blueAlliance = match.getBlue();
        for (int i = 0; i < blueAlliance.length; i++) {
          if (blueAlliance[i] == team) {
            return new TeamScoreBreakdown<>(match, (B) match.getBlueBreakdown(),
                                            Alliance.Color.BLUE, i);
          }
        }
      }

      return null;
    }

    public Collection<List<DataEntry>> getMatchEntries() {
      return matchEntries.values();
    }

    public Map<String, List<DataEntry>> getMatchEntriesRaw() {
      return matchEntries;
    }

    public Collection<List<DataEntry>> getDriveTeamEntries() {
      return driveTeamEntries.values();
    }

    public Map<String, List<DataEntry>> getDriveTeamEntriesRaw() {
      return driveTeamEntries;
    }

    public List<DataEntry> getPitEntries() {
      return pitEntries;
    }

    public Collection<TeamScoreBreakdown<B>> getScoreBreakdowns() {
      return scoreBreakdowns.values();
    }

    public TeamOpr getOpr() {
      return opr;
    }

    public RankingsCache.Team getRankings() {
      return rankings;
    }
  }

  @SuppressWarnings("java:S1105") // braces (false positive)
  protected record TeamScoreBreakdown<B extends ScoreBreakdown>(MatchInfo match,
                                                                B breakdown,
                                                                Alliance.Color allianceColor,
                                                                int stationNumber) {}

  protected static <T> Collection<T> extractData(Collection<DataEntry> entries, String path,
                                                 BiFunction<DataEntry, String, T> extractor) {
    return extractData(entries, e -> extractor.apply(e, path));
  }

  protected static <T> Collection<T> extractData(Collection<DataEntry> entries,
                                                 Function<DataEntry, T> extractor) {
    return entries.stream()
                  .map(extractor::apply)
                  .toList();
  }

  protected static <I, T>
      Collection<T>
      extractMergeData(Collection<? extends Collection<DataEntry>> entries, String path,
                       BiFunction<DataEntry, String, I> extractor,
                       Function<Collection<I>, T> reducer) {
    return extractMergeData(entries, e -> extractor.apply(e, path), reducer);
  }

  protected static <I, T>
      Collection<T>
      extractMergeData(Collection<? extends Collection<DataEntry>> entries,
                       Function<DataEntry, I> extractor, Function<Collection<I>, T> reducer) {
    return entries.stream()
                  .map(e -> extractData(e, extractor))
                  .map(reducer)
                  .toList();
  }

  protected static Double average(Iterable<? extends Number> data) {
    int count = 0;
    double sum = 0;
    for (Number num : data) {
      if (num != null) {
        sum += num.doubleValue();
        count++;
      }
    }
    return count == 0 ? null : Double.valueOf(sum / count);
  }

  protected static <T> T mostCommon(Collection<T> data) {
    Map<T, Long> counts = data.stream()
                              .collect(Collectors.groupingBy(x -> x, Collectors.counting()));
    T mostCommon = null;
    long maxCount = 0;
    for (Map.Entry<T, Long> entry : counts.entrySet()) {
      if (entry.getValue() > maxCount) {
        maxCount = entry.getValue();
        mostCommon = entry.getKey();
      }
    }
    return mostCommon;
  }

  protected static NumberSummary summarizeNumbers(Iterable<? extends Number> data) {
    int count = 0;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    double sum = 0;
    double sumSquared = 0;

    for (Number number : data) {
      if (number == null) continue;

      double val = number.doubleValue();
      sum += val;
      sumSquared += val * val;
      count++;

      if (val > max) {
        max = val;
      }
      if (val < min) {
        min = val;
      }
    }

    if (count == 0) {
      return NumberSummary.NO_DATA;
    }

    double mean = sum / count;
    double stddev = Math.sqrt(Math.abs(sumSquared - (sum * sum / count)) / count);

    return new NumberSummary(count, min, max, mean, stddev);
  }

  protected static <T extends Comparable<T>> Map<T, Integer> countDistinct(Iterable<T> data) {
    Map<T, Integer> map = new TreeMap<>(); // for sorted keys
    for (T item : data) {
      if (item != null) {
        map.compute(item, (k, count) -> count == null ? 1 : (count + 1));
      }
    }
    return map;
  }

  protected static <I, T> T mapSingle(I input, Function<I, T> mapper) {
    return input == null ? null : mapper.apply(input);
  }

  protected static <I, T> Collection<T> map(Iterable<I> data, Function<I, T> mapper) {
    Collection<T> enums = new ArrayList<>();
    for (I key : data) {
      if (key != null) {
        enums.add(mapper.apply(key));
      }
    }
    return enums;
  }

  protected static <T> Collection<T> union(Iterable<? extends Collection<T>> data) {
    Collection<T> result = new ArrayList<>();
    for (Collection<T> item : data) {
      if (item != null) {
        result.addAll(item);
      }
    }
    return result;
  }

  protected static <T> int count(Collection<T> data, Predicate<T> condition) {
    return (int) data.stream().filter(condition).count();
  }

  protected static <T> Map<T, Integer> averageCounts(Collection<Map<T, Integer>> allCounts) {
    int size = allCounts.size();
    Map<T, Integer> counts = sumCounts(allCounts);
    counts.replaceAll((key, count) -> (int) Math.round((double) count / size));
    return counts;
  }

  protected static <T> Map<T, Integer> sumCounts(Collection<Map<T, Integer>> allCounts) {
    Map<T, Integer> counts = new LinkedHashMap<>();
    for (Map<T, Integer> c : allCounts) {
      for (Map.Entry<T, Integer> entry : c.entrySet()) {
        counts.compute(entry.getKey(), (key, count) -> count == null ? entry.getValue()
            : (count + entry.getValue()));
      }
    }
    return counts;
  }

  protected static <K, V> Map<K, V> nullableMap(Collection<Map.Entry<K, V>> entries) {
    if (entries.isEmpty()) {
      return Map.of();
    }

    Map<K, V> map = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  protected static <K, V> Map.Entry<K, V> nullableMapEntry(K key, V value) {
    return new MapEntry<>(key, value);
  }

  static class MapEntry<K, V> implements Map.Entry<K, V> {
    private final K key;
    private final V value;

    MapEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V value) {
      throw new UnsupportedOperationException("Immutible");
    }
  }
}
