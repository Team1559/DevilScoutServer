package org.victorrobotics.devilscoutserver.years._2024;

import org.victorrobotics.devilscoutserver.analysis.Analyzer;
import org.victorrobotics.devilscoutserver.analysis.statistics.BooleanStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.NumberStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.OprStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.PieChartStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.RadarStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.RankingPointsStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.StatisticsPage;
import org.victorrobotics.devilscoutserver.analysis.statistics.StringStatistic;
import org.victorrobotics.devilscoutserver.analysis.statistics.WltStatistic;
import org.victorrobotics.devilscoutserver.database.DataEntry;
import org.victorrobotics.devilscoutserver.database.EntryDatabase;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache;
import org.victorrobotics.devilscoutserver.tba.OprsCache;
import org.victorrobotics.devilscoutserver.tba.RankingsCache;
import org.victorrobotics.devilscoutserver.years._2024.CrescendoEnums.DrivetrainType;
import org.victorrobotics.devilscoutserver.years._2024.CrescendoEnums.FinalStatus;
import org.victorrobotics.devilscoutserver.years._2024.CrescendoEnums.PickupLocation;
import org.victorrobotics.devilscoutserver.years._2024.CrescendoEnums.ScoreLocation;
import org.victorrobotics.devilscoutserver.years._2024.CrescendoEnums.StartPosition;

import java.util.List;
import java.util.Map;

@SuppressWarnings("java:S1192") // repeating paths is more clear here
public final class CrescendoAnalyzer extends Analyzer<CrescendoData> {
  public CrescendoAnalyzer(EntryDatabase matchEntryDB, EntryDatabase pitEntryDB,
                           EntryDatabase driveTeamEntryDB, MatchScheduleCache matchScheduleCache,
                           OprsCache teamOprsCache, RankingsCache rankingsCache) {
    super(matchEntryDB, pitEntryDB, driveTeamEntryDB, matchScheduleCache, teamOprsCache,
          rankingsCache);
  }

  @Override
  protected CrescendoData computeData(Handle handle) {
    return new CrescendoData(handle.getRankings()
                                   .getWinLossRecord(),
                             null, handle.getOpr(),
                             average(extractMergeData(handle.getDriveTeamEntries(),
                                                      "/communication", DataEntry::getInteger,
                                                      Analyzer::average)),
                             average(extractMergeData(handle.getDriveTeamEntries(), "/strategy",
                                                      DataEntry::getInteger, Analyzer::average)),
                             average(extractMergeData(handle.getDriveTeamEntries(), "/adaptability",
                                                      DataEntry::getInteger, Analyzer::average)),
                             average(extractMergeData(handle.getDriveTeamEntries(),
                                                      "/professionalism", DataEntry::getInteger,
                                                      Analyzer::average)),
                             mostCommon(map(extractData(handle.getPitEntries(), "/specs/drivetrain",
                                                        DataEntry::getInteger),
                                            DrivetrainType::of)),
                             mostCommon(extractData(handle.getPitEntries(), "/specs/weight",
                                                    DataEntry::getInteger)),
                             mostCommon(extractData(handle.getPitEntries(), "/specs/size",
                                                    DataEntry::getInteger)),
                             average(extractMergeData(handle.getMatchEntries(), "/general/speed",
                                                      DataEntry::getInteger, Analyzer::average)),
                             countDistinct(map(extractMergeData(handle.getMatchEntries(),
                                                                "/auto/start_pos",
                                                                DataEntry::getInteger,
                                                                Analyzer::mostCommon),
                                               StartPosition::of)),
                             summarizeNumbers(extractMergeData(handle.getMatchEntries(),
                                                               CrescendoAnalyzer::autoNoteCount,
                                                               Analyzer::average)),
                             summarizeNumbers(extractMergeData(handle.getMatchEntries(),
                                                               CrescendoAnalyzer::teleopCyclesPerMinute,
                                                               Analyzer::average)),
                             average(extractMergeData(handle.getMatchEntries(),
                                                      CrescendoAnalyzer::teleopScoreAccuracy,
                                                      Analyzer::average)),
                             sumCounts(extractMergeData(handle.getMatchEntries(),
                                                        CrescendoAnalyzer::teleopScoreLocations,
                                                        Analyzer::averageCounts)),
                             sumCounts(extractMergeData(handle.getMatchEntries(),
                                                        CrescendoAnalyzer::teleopPickupLocations,
                                                        Analyzer::averageCounts)),
                             countDistinct(map(extractMergeData(handle.getMatchEntries(),
                                                                "/auto/start_pos",
                                                                DataEntry::getInteger,
                                                                Analyzer::mostCommon),
                                               FinalStatus::of)),
                             average(map(extractMergeData(handle.getMatchEntries(), "/endgame/trap",
                                                          DataEntry::getBoolean,
                                                          Analyzer::mostCommon),
                                         b -> b ? 1 : 0)));
  }

  @Override
  protected List<StatisticsPage> generateStatistics(CrescendoData data) {
    return List.of(new StatisticsPage("Summary",
                                      List.of(new WltStatistic(data.wlt()),
                                              new RankingPointsStatistic(data.rankingPoints()),
                                              new OprStatistic(data.opr()), driveTeamRadar(data))),
                   new StatisticsPage("Specs",
                                      List.of(new StringStatistic("Drivetrain", data.drivetrain()),
                                              new StringStatistic("Weight", data.weight(), " lbs"),
                                              new StringStatistic("Size", data.size(), " in"),
                                              new StringStatistic("Speed", data.speed(), " / 5"))),
                   new StatisticsPage("Auto",
                                      List.of(new PieChartStatistic("Start Position",
                                                                    data.autoStartPositions()),
                                              new NumberStatistic("Note Count", data.autoNotes()))),
                   new StatisticsPage("Teleop",
                                      List.of(new NumberStatistic("Cycles per Minute",
                                                                  data.teleopCyclesPerMinute()),
                                              new BooleanStatistic("Score Accuracy",
                                                                   data.teleopScoreAccuracy()),
                                              new PieChartStatistic("Score Locations",
                                                                    data.teleopScoreCounts()),
                                              new PieChartStatistic("Pickup Locations",
                                                                    data.teleopPickupCounts()))),
                   new StatisticsPage("Endgame",
                                      List.of(new PieChartStatistic("Final Status",
                                                                    data.endgameStatusCounts()),
                                              new BooleanStatistic("Trap Rate", data.trapRate()))));
  }

  private static RadarStatistic driveTeamRadar(CrescendoData data) {
    return new RadarStatistic("Drive Team", 5,
                              nullableMap(List.of(nullableMapEntry("Communication",
                                                                   data.driveTeamCommunication()),
                                                  nullableMapEntry("Strategy",
                                                                   data.driveTeamStrategy()),
                                                  nullableMapEntry("Adaptability",
                                                                   data.driveTeamAdaptability()),
                                                  nullableMapEntry("Professionalism",
                                                                   data.driveTeamProfessionalism()))));
  }

  private static Integer autoNoteCount(DataEntry match) {
    List<Integer> actions = match.getIntegers("/auto/routine");
    int scoreCount = 0;
    int pickupCount = 0;
    for (Integer action : actions) {
      switch (action) {
        case 0, 1:
          scoreCount++;
          break;
        case 2:
          break;
        case 3:
          pickupCount++;
          break;
        default:
          return null;
      }
    }
    // Can't score more than you pick up
    return Math.min(scoreCount, pickupCount + 1);
  }

  private static Double teleopCyclesPerMinute(DataEntry match) {
    // We are assuming 2 minutes of play time
    return (match.getInteger("/teleop/score_amp") + match.getInteger("/teleop/score_speaker"))
        / 2.0;
  }

  private static Double teleopScoreAccuracy(DataEntry match) {
    Integer sourcePickups = match.getInteger("/teleop/pickup_source");
    Integer groundPickups = match.getInteger("/teleop/pickup_ground");

    int attempts = 0;
    if (sourcePickups != null) {
      attempts += sourcePickups;
    }
    if (groundPickups != null) {
      attempts += groundPickups;
    }

    if (attempts == 0) return null;

    Integer speakerScores = match.getInteger("/teleop/score_speaker");
    Integer ampScores = match.getInteger("/teleop/score_amp");

    if (speakerScores == null && ampScores == null) {
      return null;
    }

    int scores = 0;
    if (speakerScores != null) {
      scores += speakerScores;
    }
    if (ampScores != null) {
      scores += ampScores;
    }

    return Math.clamp((double) scores / attempts, 0, 1);
  }

  private static Map<ScoreLocation, Integer> teleopScoreLocations(DataEntry match) {
    return Map.of(ScoreLocation.SPEAKER, match.getInteger("/teleop/score_speaker"),
                  ScoreLocation.AMP, match.getInteger("/teleop/score_amp"));
  }

  private static Map<PickupLocation, Integer> teleopPickupLocations(DataEntry match) {
    return Map.of(PickupLocation.GROUND, match.getInteger("/teleop/pickup_ground"),
                  PickupLocation.SOURCE, match.getInteger("/teleop/pickup_source"));
  }
}
