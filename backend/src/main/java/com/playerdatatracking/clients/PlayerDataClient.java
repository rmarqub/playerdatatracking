package com.playerdatatracking.clients;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.playerdatatracking.entities.indexaldata.Club;
import com.playerdatatracking.entities.indexaldata.ClubInLeague;
import com.playerdatatracking.entities.indexaldata.PlayerPhotoData;
import com.playerdatatracking.entities.indexaldata.ConfigParams;
import com.playerdatatracking.entities.indexaldata.DuppedPlayers;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.entities.indexaldata.PLAYER_QUALITIES;
import com.playerdatatracking.entities.indexaldata.Pais;
import com.playerdatatracking.entities.indexaldata.Player;
import com.playerdatatracking.entities.indexaldata.Torneo;
import com.playerdatatracking.entities.indexaldata.Transfer;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.entities.indexaldata.Fixture;
import com.playerdatatracking.repositories.indexaldata.ClubInLeagueRepository;
import com.playerdatatracking.repositories.indexaldata.ClubRepository;
import com.playerdatatracking.repositories.indexaldata.ConfigParamsRepository;
import com.playerdatatracking.repositories.indexaldata.DuppedPlayerRepository;
import com.playerdatatracking.entities.indexaldata.FixtureEvent;
import com.playerdatatracking.entities.indexaldata.FixtureTeamStats;
import com.playerdatatracking.entities.indexaldata.FixturePlayerStats;
import com.playerdatatracking.entities.indexaldata.FixtureLineup;
import com.playerdatatracking.repositories.indexaldata.FixtureEventRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureTeamStatsRepository;
import com.playerdatatracking.repositories.indexaldata.FixturePlayerStatsRepository;
import com.playerdatatracking.repositories.indexaldata.FixtureLineupRepository;
import com.playerdatatracking.repositories.indexaldata.MANUAL_TRACKED_PLAYERRepository;
import com.playerdatatracking.repositories.indexaldata.PLAYER_QUALITIESRepository;
import com.playerdatatracking.repositories.indexaldata.PaisRepository;
import com.playerdatatracking.repositories.indexaldata.PlayerRepository;
import com.playerdatatracking.repositories.indexaldata.PlayerStatsRepository;
import com.playerdatatracking.repositories.indexaldata.SquadRepository;
import com.playerdatatracking.repositories.indexaldata.TorneoRepository;
import com.playerdatatracking.repositories.indexaldata.TransferRepository;
import com.playerdatatracking.repositories.keys.API_FOOTBALL_KEYSRepository;
import com.playerdatatracking.requests.IndexTeamPair;
import com.playerdatatracking.requests.PlayerMatchRow;
import com.playerdatatracking.responses.H2HBestPlayer;
import com.playerdatatracking.responses.H2HComparisonData;
import com.playerdatatracking.responses.H2HFixtureSummary;
import com.playerdatatracking.responses.H2HGoalScorer;

import jakarta.transaction.Transactional;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PlayerDataClient {

	
	@Autowired
	private MANUAL_TRACKED_PLAYERRepository mpRepository;
	@Autowired
	private PLAYER_QUALITIESRepository pqRepository;
	@Autowired
	private API_FOOTBALL_KEYSRepository akRepository;
	@Autowired
	private PaisRepository ctRepository;
	@Autowired
	private TorneoRepository trRepository;
	@Autowired
	private ConfigParamsRepository cpRepository;
	@Autowired
	private ClubRepository clubRepository;
	@Autowired
	private ClubInLeagueRepository cilRepository;
	@Autowired
	private PlayerRepository pRepository;
	@Autowired
	private DuppedPlayerRepository dpRepository;
	@Autowired
	private TransferRepository tRepository;
	@Autowired
	private SquadRepository sRepository;
	@Autowired
	private PlayerStatsRepository psRepository;
	@Autowired
	private FixtureRepository fixtureRepository;
	@Autowired
	private FixtureEventRepository fixtureEventRepository;
	@Autowired
	private FixtureTeamStatsRepository fixtureTeamStatsRepository;
	@Autowired
	private FixturePlayerStatsRepository fixturePlayerStatsRepository;
	@Autowired
	private FixtureLineupRepository fixtureLineupRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	
	@Transactional
	public boolean saveTransfer(Transfer t) throws PlayerDataDBException {
		try {
			tRepository.save(t);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public boolean saveDuppedPlayer(DuppedPlayers d) throws PlayerDataDBException{
		try {
			dpRepository.save(d);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public List<Transfer> getTransfersByPlayers(Long indexId) throws PlayerDataDBException{
		try {
			return tRepository.findByPlayer(indexId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public List<DuppedPlayers> getDuppedPlayerById(Long indexId) throws PlayerDataDBException{
		try {
			return dpRepository.findByPlayer(indexId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public boolean deleteIndexedPlayer(Long indexid, Long teamid) throws PlayerDataDBException{
		try {
			Optional<Club> oClub =clubRepository.findById(teamid);
			Club c = oClub.isPresent() ? oClub.get() : null;
			if (c!=null) {
				List<Player> response = pRepository.findByTeamAndIndexId(c, indexid);
				if (!response.isEmpty()) {
					for(Player p : response) {
						pRepository.delete(p);
					}
					return true;
				}
				else
					throw new PlayerDataDBException("Club " + teamid + " of the player " + indexid +" not found");
			}
			else
				throw new PlayerDataDBException("Club " + teamid + " of the player " + indexid +" not found");
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<Player> getPlayerByIndexIdAndTeam(Long teamid, Long indexid) throws PlayerDataDBException{
		try {
			Optional<Club> oClub =clubRepository.findById(teamid);
			Club c = oClub.isPresent() ? oClub.get() : null;
			if (c!=null) {
				List<Player> response = pRepository.findByTeamAndIndexId(c, indexid);
				if (response==null || response.isEmpty())
					return new ArrayList<Player>();
				else
					return response;
			}
			else
				throw new PlayerDataDBException("Club " + teamid + " of the player " + indexid +" not found");
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public Long getPlayerIdByIndexId(Long indexId) throws PlayerDataDBException {
		try {
			return pRepository.findFirstByIndexIdOrderByIdDesc(indexId)
					.map(Player::getId)
					.orElse(null);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<IndexTeamPair> getDuppedPlayersWithDiffTeam() throws PlayerDataDBException{
		try {
			List<IndexTeamPair> l = pRepository.findIndexIdTeamPairsWithCrossTeamDuplicates();
			return l;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public boolean savePlayer(ManualTrackedPlayer player) throws PlayerDataDBException {
		try {
			mpRepository.save(player);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public ManualTrackedPlayer getPlayerbyName(String name) throws PlayerDataDBException {
		try {
			return mpRepository.findByNombre(name);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public List<ManualTrackedPlayer> getPlayersByUserId(Long userId) {
        return mpRepository.findAllByUserId(userId);
    }
	
	@Transactional
	public ManualTrackedPlayer getPlayerByNameAndUserId(String nombre, Long userId) throws PlayerDataDBException {
		try {
			Optional<ManualTrackedPlayer> p = mpRepository.findByNombreAndUserId(nombre, userId);
			if (p.isPresent())
				return p.get();
			else
				throw new IllegalArgumentException("Player already registered for this user"); 
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<ManualTrackedPlayer> getAllPlayers() {
		return mpRepository.findAll();
	}
	
	@Transactional
	public List<String> getAllQualities (Long player) throws PlayerDataDBException{
		try {
			List<String> response = new ArrayList<>();
			List<PLAYER_QUALITIES> pqList = pqRepository.findByPlayerId(player);
			if (pqList.size()==0)
				return null;
			for (PLAYER_QUALITIES q : pqList) {
				response.add(q.getQuality());
			}
			return response;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public boolean saveApiKey(Keys newKey) throws PlayerDataDBException{
		try{
			akRepository.save(newKey);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Keys getKeyByKey (String key) throws PlayerDataDBException{
		try {
			Keys storedKey = akRepository.findByValor(key);
			return storedKey;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Keys getKeyByHash (String key) throws PlayerDataDBException{
		try {
			Keys storedKey = akRepository.findByHashKey(key);
			return storedKey;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Keys getValidKey() throws PlayerDataDBException{
		try {
			List<Keys> storedKeys = akRepository.findByIsValid(true);
			if (storedKeys != null)
				if (storedKeys.size()>0)
					return storedKeys.get(0);
			return null;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Pais findCountry(String name) throws PlayerDataDBException{
		try {
			Pais country = ctRepository.findByName(name);
			if (country!=null)
				return country;
			return null;
		}
		catch(Exception e){
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Pais findCountry(Long id) throws PlayerDataDBException{
		try {
			Optional<Pais> country = ctRepository.findById(id);
			if (country!=null && country.isPresent())
				return country.get();
			return null;
		}
		catch(Exception e){
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public boolean deleteKey (Keys key) throws PlayerDataDBException{
		if (key==null)
			throw new PlayerDataDBException("no se puede borrar una apikey que sea nula");
		try {
			akRepository.delete(key);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
		
	}
	
	@Transactional
	public boolean deletePlayer(String name) throws PlayerDataDBException{
		try {
			ManualTrackedPlayer player = mpRepository.findByNombre(name);
			if (player==null)
				throw new PlayerDataDBException("no player was found with name " + name);
			mpRepository.delete(player);
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<Keys> getKeysByMail (String mail)throws PlayerDataDBException{
		try {
			List<Keys> storedKeys = akRepository.findByMail(mail);
			if(storedKeys!=null)
				return storedKeys;
			return new ArrayList<Keys>();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<Keys> getAllKeys() throws PlayerDataDBException{
		try {
			return akRepository.findAll();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public ManualTrackedPlayer getStudiedPlayer(Long id) throws PlayerDataDBException{
		try {
			Optional<ManualTrackedPlayer> player = mpRepository.findById(id);
			return player.isPresent() ? player.get() : null;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Player getPlayer(Long id) throws PlayerDataDBException{
		try {
			Optional<Player> player = pRepository.findById(id);
			return player.isPresent() ? player.get() : null;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public boolean saveCountry(Pais pais) throws PlayerDataDBException{
		try {
			ctRepository.save(pais);
			return true;
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	
	@Transactional
	public void deleteAllCountries() throws PlayerDataDBException{
		try {
			ctRepository.deleteAll();
			jdbcTemplate.execute("ALTER SEQUENCE pais_id_seq RESTART WITH 1");
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public boolean saveTorneo(Torneo tr) throws PlayerDataDBException{
		try {
			trRepository.save(tr);
			return true;
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public void deleteAllTorneos() throws PlayerDataDBException{
		try {
			trRepository.deleteAll();
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public ConfigParams getParam(String key) throws PlayerDataDBException{
		try {
			ConfigParams param = cpRepository.findByKey(key);
			return param;
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public boolean deleteAllClubs() throws PlayerDataDBException{
		try {
			clubRepository.deleteAll();
			return true;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	@Transactional
	public List<Torneo> getStudiedLeagues() throws PlayerDataDBException{
		try {
			List<Torneo> response = trRepository.findByStudied(true);
			return response;
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Torneo getTorneoById(Long id) throws PlayerDataDBException{
		try {
			Optional<Torneo> opTorneo = trRepository.findById(id);
			return opTorneo.isPresent() ? opTorneo.get() : null;
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Club saveClub(Club club) throws PlayerDataDBException {
		try {
			Club savedClub = clubRepository.save(club);
			return savedClub;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public void clubPlaysInLeague(ClubInLeague cil) throws PlayerDataDBException{
		try {
			cilRepository.save(cil);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deleteAllCILs() throws PlayerDataDBException{
		try {
			cilRepository.deleteAll();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Club findClub(Long id) throws PlayerDataDBException{
		try {
			Optional<Club> club = clubRepository.findById(id);
			return club.isPresent() ? club.get() : null;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public ClubInLeague findCIL(Long club, Long torneo) throws PlayerDataDBException{
		try {
			Optional<ClubInLeague> resultado = cilRepository.findByClubIdAndTorneoId(club, torneo);
			return resultado.orElse(null);
		} catch(Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public void deleteAllPlayers() throws PlayerDataDBException{
		try {
			pRepository.deleteAll();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<Club> getAllClubs() throws PlayerDataDBException{
		try {
			List<Club> response = clubRepository.findAll();
			return response;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public List<ClubInLeague> findCILsByClub(Long idClub)throws PlayerDataDBException{
		try {
			return cilRepository.findByClubId(idClub);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	@Transactional
	public Player saveIndexedPlayer(Player p) throws PlayerDataDBException{
		try {
			return pRepository.save(p);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}
	
	public List<Player> searchPlayers(String playerName, String teamName) {
        if (playerName != null && !playerName.isEmpty()) {
            return pRepository.findByPlayerName(playerName);
        } else if (teamName != null && !teamName.isEmpty()) {
            return pRepository.findByTeam_NombreContainingIgnoreCase(teamName);
        }
        return new ArrayList<>();
    }
	
	public Player searchPlayer(Long id) throws PlayerDataDBException {
		try {
			Optional<Player> response = pRepository.findById(id);
			return response.isPresent() ? response.get() : null;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
    }
	
	public List<PlayerMatchRow> getStaticsByIndexId(Long indexId) {
	    return psRepository.findByIndexId(indexId);
	}
	
	@Transactional
	public List<Fixture> getLiveFixtures() throws PlayerDataDBException {
		try {
			return fixtureRepository.findLiveFixtures();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> searchFixturesByTeam(String teamName) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByTeamNameContaining(teamName);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public Fixture saveFixture(Fixture f) throws PlayerDataDBException {
		try {
			return fixtureRepository.save(f);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> saveAllFixtures(List<Fixture> fixtures) throws PlayerDataDBException {
		try {
			return fixtureRepository.saveAll(fixtures);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deleteFixturesByLeagueAndSeason(Integer leagueId, Integer season) throws PlayerDataDBException {
		try {
			fixtureRepository.deleteByLeagueIdAndSeason(leagueId, season);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getExistingFixtureIds(Integer leagueId, Integer season) throws PlayerDataDBException {
		try {
			return fixtureRepository.findIdsByLeagueAndSeason(leagueId, season);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void updateFixtureStatus(Fixture f) throws PlayerDataDBException {
		try {
			fixtureRepository.updateStatusAndScore(
				f.getId(),
				f.getStatusShort(), f.getStatusLong(), f.getStatusElapsed(), f.getStatusExtra(),
				f.getGoalsHome(), f.getGoalsAway(),
				f.getScoreHtHome(), f.getScoreHtAway(),
				f.getScoreFtHome(), f.getScoreFtAway(),
				f.getScoreEtHome(), f.getScoreEtAway(),
				f.getScorePenHome(), f.getScorePenAway(),
				f.getReferee(),
				java.time.OffsetDateTime.now()
			);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getAllFixtureIds() throws PlayerDataDBException {
		try {
			return fixtureRepository.findAllIds();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getFixtureIdsWithoutEvents() throws PlayerDataDBException {
		try {
			return fixtureRepository.findIdsWithoutEvents();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void saveAllFixtureEvents(Long fixtureId, List<FixtureEvent> events) throws PlayerDataDBException {
		try {
			Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
			for (FixtureEvent event : events)
				event.setFixture(fixtureRef);
			fixtureEventRepository.saveAll(events);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deleteEventsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureEventRepository.deleteByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public boolean hasFixtureEvents(Long fixtureId) throws PlayerDataDBException {
		try {
			return fixtureEventRepository.existsByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void markFixtureEventsStored(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureRepository.markEventsStored(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getFixtureIdsWithoutTeamStats() throws PlayerDataDBException {
		try {
			return fixtureRepository.findIdsWithoutTeamStats();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getFixtureIdsWithoutPlayerStats() throws PlayerDataDBException {
		try {
			return fixtureRepository.findIdsWithoutPlayerStats();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void saveAllFixtureTeamStats(Long fixtureId, List<FixtureTeamStats> stats) throws PlayerDataDBException {
		try {
			Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
			for (FixtureTeamStats s : stats)
				s.setFixture(fixtureRef);
			fixtureTeamStatsRepository.saveAll(stats);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void saveAllFixturePlayerStats(Long fixtureId, List<FixturePlayerStats> stats) throws PlayerDataDBException {
		try {
			Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
			for (FixturePlayerStats s : stats)
				s.setFixture(fixtureRef);
			fixturePlayerStatsRepository.saveAll(stats);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deleteTeamStatsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureTeamStatsRepository.deleteByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deletePlayerStatsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			fixturePlayerStatsRepository.deleteByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void markFixtureMatchStored(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureRepository.markMatchStored(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void markFixtureStatsStored(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureRepository.markStatsStored(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Long> getFixtureIdsWithoutLineups() throws PlayerDataDBException {
		try {
			return fixtureRepository.findIdsWithoutLineups();
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void saveAllFixtureLineups(Long fixtureId, List<FixtureLineup> lineups) throws PlayerDataDBException {
		try {
			Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
			for (FixtureLineup l : lineups)
				l.setFixture(fixtureRef);
			fixtureLineupRepository.saveAll(lineups);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void deleteLineupsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureLineupRepository.deleteByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public void markFixtureLineupStored(Long fixtureId) throws PlayerDataDBException {
		try {
			fixtureRepository.markLineupStored(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> searchFixturesByLeague(Integer leagueId) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByLeagueIdOrderByMatchDateDesc(leagueId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Club> findClubsByNameContaining(String name) throws PlayerDataDBException {
		try {
			return clubRepository.findByNombreContainingIgnoreCase(name);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> searchFixturesByTeamIds(List<Long> teamIds) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByTeamIds(teamIds);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> searchFixturesByTeamIdsAndLeagueIds(List<Long> teamIds, List<Integer> leagueIds) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByTeamIdsAndLeagueIds(teamIds, leagueIds);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<Fixture> searchFixturesByLeagueIds(List<Integer> leagueIds) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByLeagueIds(leagueIds);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public Fixture getFixtureById(Long id) throws PlayerDataDBException {
		try {
			return fixtureRepository.findById(id).orElse(null);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<FixtureEvent> getFixtureEventsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			return fixtureEventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<FixtureTeamStats> getFixtureTeamStatsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			return fixtureTeamStatsRepository.findByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<FixturePlayerStats> getFixturePlayerStatsByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			return fixturePlayerStatsRepository.findByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<FixtureLineup> getFixtureLineupByFixtureId(Long fixtureId) throws PlayerDataDBException {
		try {
			return fixtureLineupRepository.findByFixtureId(fixtureId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public List<H2HFixtureSummary> getH2HFixtures(Long fixtureId) throws PlayerDataDBException {
		try {
			Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
			if (fixture == null) return new ArrayList<>();

			Long team1 = fixture.getHomeTeamId();
			Long team2 = fixture.getAwayTeamId();

			List<Fixture> h2hList = fixtureRepository.findHeadToHead(team1, team2);
			List<H2HFixtureSummary> result = new ArrayList<>();

			for (Fixture f : h2hList) {
				H2HFixtureSummary summary = new H2HFixtureSummary();
				summary.setFixtureId(f.getId());
				summary.setMatchDate(f.getMatchDate() != null ? f.getMatchDate().toString() : null);
				summary.setSeason(f.getSeason());
				summary.setLeagueName(f.getLeagueName());
				summary.setRound(f.getRound());
				summary.setHomeTeamId(f.getHomeTeamId());
				summary.setHomeTeamName(f.getHomeTeamName());
				summary.setAwayTeamId(f.getAwayTeamId());
				summary.setAwayTeamName(f.getAwayTeamName());
				summary.setGoalsHome(f.getGoalsHome());
				summary.setGoalsAway(f.getGoalsAway());

				List<FixtureEvent> goals = fixtureEventRepository.findGoalsByFixture(f.getId());
				List<H2HGoalScorer> scorers = new ArrayList<>();
				for (FixtureEvent ev : goals) {
					H2HGoalScorer s = new H2HGoalScorer();
					s.setPlayerId(ev.getPlayerId());
					s.setPlayerName(ev.getPlayerName());
					s.setTeamId(ev.getTeamId());
					s.setMinute(ev.getTimeElapsed());
					s.setMinuteExtra(ev.getTimeExtra());
					s.setDetail(ev.getEventDetail());
					scorers.add(s);
				}
				summary.setScorers(scorers);

				List<FixturePlayerStats> playerStats = fixturePlayerStatsRepository.findByFixtureId(f.getId());
				List<H2HBestPlayer> bestPlayers = new ArrayList<>();
				for (long teamId : new long[]{f.getHomeTeamId(), f.getAwayTeamId()}) {
					final long tid = teamId;
					playerStats.stream()
						.filter(ps -> ps.getTeamId() != null && ps.getTeamId() == tid && ps.getRating() != null)
						.sorted((a, b) -> b.getRating().compareTo(a.getRating()))
						.limit(2)
						.forEach(ps -> {
							H2HBestPlayer bp = new H2HBestPlayer();
							bp.setPlayerId(ps.getPlayerId());
							bp.setPlayerName(ps.getPlayerName());
							bp.setTeamId(ps.getTeamId());
							bp.setRating(ps.getRating().doubleValue());
							bestPlayers.add(bp);
						});
				}
				summary.setBestPlayers(bestPlayers);
				result.add(summary);
			}
			return result;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public H2HComparisonData getH2HComparison(Long fixtureId) throws PlayerDataDBException {
		try {
			Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
			if (fixture == null) return null;

			Long team1Id = fixture.getHomeTeamId();
			Long team2Id = fixture.getAwayTeamId();

			List<Fixture> h2hList = fixtureRepository.findHeadToHead(team1Id, team2Id);

			H2HComparisonData data = new H2HComparisonData();
			data.setTeam1Id(team1Id);
			data.setTeam1Name(fixture.getHomeTeamName());
			data.setTeam2Id(team2Id);
			data.setTeam2Name(fixture.getAwayTeamName());
			data.setTotalMatches(h2hList.size());

			int wins1 = 0, wins2 = 0, draws = 0, goals1 = 0, goals2 = 0;
			double sumPoss1 = 0, sumPoss2 = 0, sumShots1 = 0, sumShots2 = 0;
			double sumShotsOn1 = 0, sumShotsOn2 = 0, sumCorners1 = 0, sumCorners2 = 0;
			double sumFouls1 = 0, sumFouls2 = 0, sumYellow1 = 0, sumYellow2 = 0;
			double sumxG1 = 0, sumxG2 = 0;
			int statsCount = 0;

			for (Fixture f : h2hList) {
				boolean t1IsHome = f.getHomeTeamId().equals(team1Id);
				int g1 = t1IsHome ? safeInt(f.getGoalsHome()) : safeInt(f.getGoalsAway());
				int g2 = t1IsHome ? safeInt(f.getGoalsAway()) : safeInt(f.getGoalsHome());
				goals1 += g1;
				goals2 += g2;
				if (g1 > g2) wins1++;
				else if (g2 > g1) wins2++;
				else draws++;

				Optional<FixtureTeamStats> s1Opt = fixtureTeamStatsRepository.findByFixtureIdAndTeamId(f.getId(), team1Id);
				Optional<FixtureTeamStats> s2Opt = fixtureTeamStatsRepository.findByFixtureIdAndTeamId(f.getId(), team2Id);
				if (s1Opt.isPresent() && s2Opt.isPresent()) {
					FixtureTeamStats s1 = s1Opt.get();
					FixtureTeamStats s2 = s2Opt.get();
					statsCount++;
					sumPoss1   += safeDec(s1.getBallPossession());
					sumPoss2   += safeDec(s2.getBallPossession());
					sumShots1  += safeInt(s1.getShotsTotal());
					sumShots2  += safeInt(s2.getShotsTotal());
					sumShotsOn1 += safeInt(s1.getShotsOnGoal());
					sumShotsOn2 += safeInt(s2.getShotsOnGoal());
					sumCorners1 += safeInt(s1.getCornerKicks());
					sumCorners2 += safeInt(s2.getCornerKicks());
					sumFouls1  += safeInt(s1.getFouls());
					sumFouls2  += safeInt(s2.getFouls());
					sumYellow1 += safeInt(s1.getYellowCards());
					sumYellow2 += safeInt(s2.getYellowCards());
					sumxG1     += safeDec(s1.getExpectedGoals());
					sumxG2     += safeDec(s2.getExpectedGoals());
				}
			}

			data.setTeam1Wins(wins1);
			data.setTeam2Wins(wins2);
			data.setDraws(draws);
			data.setTeam1Goals(goals1);
			data.setTeam2Goals(goals2);

			if (statsCount > 0) {
				data.setTeam1AvgPossession(r2(sumPoss1 / statsCount));
				data.setTeam2AvgPossession(r2(sumPoss2 / statsCount));
				data.setTeam1AvgShots(r2(sumShots1 / statsCount));
				data.setTeam2AvgShots(r2(sumShots2 / statsCount));
				data.setTeam1AvgShotsOnTarget(r2(sumShotsOn1 / statsCount));
				data.setTeam2AvgShotsOnTarget(r2(sumShotsOn2 / statsCount));
				data.setTeam1AvgCorners(r2(sumCorners1 / statsCount));
				data.setTeam2AvgCorners(r2(sumCorners2 / statsCount));
				data.setTeam1AvgFouls(r2(sumFouls1 / statsCount));
				data.setTeam2AvgFouls(r2(sumFouls2 / statsCount));
				data.setTeam1AvgYellowCards(r2(sumYellow1 / statsCount));
				data.setTeam2AvgYellowCards(r2(sumYellow2 / statsCount));
				data.setTeam1AvgxG(r2(sumxG1 / statsCount));
				data.setTeam2AvgxG(r2(sumxG2 / statsCount));
			}
			return data;
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	private int safeInt(Integer v) { return v != null ? v : 0; }
	private double safeDec(java.math.BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }
	private double r2(double v) { return Math.round(v * 100.0) / 100.0; }

	@Transactional
	public byte[] getPhoto(Long id) { return pRepository.findPhotoById(id); }

	@Transactional
	public String getPhotoContentType(Long id) { return pRepository.findPhotoContentTypeById(id); }

	@Transactional
	public LocalDateTime getPhotoUpdatedAt(Long id) { return pRepository.findPhotoUpdatedAtById(id); }

	@Transactional
	public PlayerPhotoData getPhotoData(Long id) { return pRepository.findPhotoDataById(id); }
}
