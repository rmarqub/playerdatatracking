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

	  return jdbc.query(
			  sql,
			  ps -> ps.setLong(1, indexId),
			  new org.springframework.jdbc.core.BeanPropertyRowMapper<>(PlayerMatchRow.class)
			);
  }
}