package org.victorrobotics.devilscoutserver.analysis._2024;

import org.victorrobotics.devilscoutserver.analysis.Analyzer;
import org.victorrobotics.devilscoutserver.analysis.statistics.StatisticsPage;
import org.victorrobotics.devilscoutserver.database.EntryDatabase;
import org.victorrobotics.devilscoutserver.tba.EventOprsCache;
import org.victorrobotics.devilscoutserver.tba.EventWltCache;
import org.victorrobotics.devilscoutserver.tba.MatchScheduleCache;

import java.util.List;

public final class CrescendoAnalyzer extends Analyzer {
  public CrescendoAnalyzer(EntryDatabase matchEntryDB, EntryDatabase pitEntryDB,
                           EntryDatabase driveTeamEntryDB, MatchScheduleCache<?> matchScheduleCache,
                           EventOprsCache teamOprsCache, EventWltCache eventWltCache) {
    super(matchEntryDB, pitEntryDB, driveTeamEntryDB, matchScheduleCache, teamOprsCache,
          eventWltCache);
  }

  @Override
  protected List<StatisticsPage> computeStatistics(DataHandle handle) {
    return List.of(new StatisticsPage("title", List.of(handle.rpStatistic("test"))));
  }
}
