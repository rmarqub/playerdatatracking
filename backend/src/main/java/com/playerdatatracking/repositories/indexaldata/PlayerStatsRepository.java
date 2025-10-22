package com.playerdatatracking.repositories.indexaldata;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.requests.PlayerMatchRow;

@Repository
public class PlayerStatsRepository {
  private final JdbcTemplate jdbc;
  public PlayerStatsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public List<PlayerMatchRow> findByIndexId(Long indexId) {
    final String sql =
      "SELECT player_id, player_name, team_id, team_name, " +
      "       league_id, league_name, season, match_bucket, " +
      "       minutes, position, rating, " +
      "       shots_total, shots_on, goals, assists, " +
      "       passes_total, passes_key, passes_acc, " +
      "       tackles_total, interceptions, duels_total, duels_won, " +
      "       dribbles_att, dribbles_suc, fouls_drawn, fouls_comm, yc, rc " +
      "FROM player_match_stats " +
      "WHERE player_id = ? " +
      "ORDER BY season DESC NULLS LAST, league_name ASC";

    List<PlayerMatchRow> response = (List<PlayerMatchRow>) jdbc.query(sql, rs -> {
      var r = new PlayerMatchRow();
      r.playerId = rs.getLong("player_id");
      r.playerName = rs.getString("player_name");
      r.teamId = (Integer)rs.getObject("team_id");
      r.teamName = rs.getString("team_name");
      r.leagueId = (Integer)rs.getObject("league_id");
      r.leagueName = rs.getString("league_name");
      r.season = rs.getString("season");
      r.matchBucket = rs.getString("match_bucket");
      r.minutes = (Integer)rs.getObject("minutes");
      r.position = rs.getString("position");
      r.rating = rs.getBigDecimal("rating");
      r.shotsTotal = (Integer)rs.getObject("shots_total");
      r.shotsOn = (Integer)rs.getObject("shots_on");
      r.goals = (Integer)rs.getObject("goals");
      r.assists = (Integer)rs.getObject("assists");
      r.passesTotal = (Integer)rs.getObject("passes_total");
      r.passesKey = (Integer)rs.getObject("passes_key");
      r.passesAcc = (Integer)rs.getObject("passes_acc");
      r.tacklesTotal = (Integer)rs.getObject("tackles_total");
      r.interceptions = (Integer)rs.getObject("interceptions");
      r.duelsTotal = (Integer)rs.getObject("duels_total");
      r.duelsWon = (Integer)rs.getObject("duels_won");
      r.dribblesAtt = (Integer)rs.getObject("dribbles_att");
      r.dribblesSuc = (Integer)rs.getObject("dribbles_suc");
      r.foulsDrawn = (Integer)rs.getObject("fouls_drawn");
      r.foulsComm = (Integer)rs.getObject("fouls_comm");
      r.yc = (Integer)rs.getObject("yc");
      r.rc = (Integer)rs.getObject("rc");
      return r;
    }, indexId);
    return response;
  }
}