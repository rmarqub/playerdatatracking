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
import com.playerdatatracking.repositories.indexaldata.FixtureRepository;
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
	public List<Fixture> searchFixturesByLeague(Integer leagueId) throws PlayerDataDBException {
		try {
			return fixtureRepository.findByLeagueIdOrderByMatchDateDesc(leagueId);
		} catch (Exception e) {
			throw new PlayerDataDBException(e.getMessage());
		}
	}

	@Transactional
	public byte[] getPhoto(Long id) { return pRepository.findPhotoById(id); }

	@Transactional
	public String getPhotoContentType(Long id) { return pRepository.findPhotoContentTypeById(id); }

	@Transactional
	public LocalDateTime getPhotoUpdatedAt(Long id) { return pRepository.findPhotoUpdatedAtById(id); }

	@Transactional
	public PlayerPhotoData getPhotoData(Long id) { return pRepository.findPhotoDataById(id); }
}
