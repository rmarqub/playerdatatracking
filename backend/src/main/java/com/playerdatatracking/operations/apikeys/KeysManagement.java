package com.playerdatatracking.operations.apikeys;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.crypto.AESCrypto;
import com.playerdatatracking.entities.keys.Keys;
import com.playerdatatracking.exceptions.apikeys.ApiKeyManagementException;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.responses.GenericResponse;

@Component
public class KeysManagement {

	@Autowired
	private AESCrypto crypt;
	@Autowired
	private PlayerDataClient pdClient;
	@Autowired
	private Environment env;
	SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");


	public void setEnv(Environment extenv) {
		this.env = extenv;
	}

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	
	public void useKey(Keys actualKey) throws Exception {
		try {
			String actualDate = formatter.format(new Date());
			actualKey.setLastUsed(actualDate);
			actualKey.setTodayUses(actualKey.getTodayUses()+1);
			if (actualKey.getTodayUses()>= actualKey.getTotalUses()) {
				actualKey.setValid(false);
				throw new ApiKeyManagementException("no hay almacenada ninguna key valida");
			}
			else
				actualKey.setValid(true);
			pdClient.saveApiKey(actualKey);
		} catch (Exception e) {
			throw e;
		}
	}
	public boolean deleteKey(Keys key) throws PlayerDataDBException {
		try {
			return pdClient.deleteKey(key);
		} catch (Exception e) {
			throw e;
		}
		
	}
	public void storeUsedKey(Keys key) throws Exception {
		try {
			key.setValor(crypt.encrypt(key.getValor()));
			pdClient.saveApiKey(key);
		} catch (Exception e) {
			throw e;
		}
	}
	public Keys setNewPlan(String key, String newPlan) throws Exception {
		try {
			Keys storedKey = getKeyByKey(key).getEntity();
			pdClient.deleteKey(storedKey);
			switch (newPlan) {
				case Constants.APISPORTS_FREE:
					storedKey.setTotalUses(Constants.USAGES_APISPORTS_FREE);
					break;
				case Constants.APISPORTS_MEGA:
					storedKey.setTotalUses(Constants.USAGES_APISPORTS_MEGA);
					break;
				case Constants.APISPORTS_PRO:
					storedKey.setTotalUses(Constants.USAGES_APISPORTS_PRO);
					break;
				case Constants.APISPORTS_ULTRA:
					storedKey.setTotalUses(Constants.USAGES_APISPORTS_ULTRA);
					break;
				default:
					break;	
		}
			storedKey.setValid(true);
			pdClient.saveApiKey(storedKey);
			return storedKey;
		} catch (Exception e) {
			throw e;
		}
	}
	public Keys SuscribeKeys(String key)throws Exception {
		try{
			Keys storedKey = getKeyByKey(key).getEntity();
			pdClient.deleteKey(storedKey);
			storedKey.setPermanentlyValid(true);
			pdClient.saveApiKey(storedKey);
			return storedKey;
		}catch (Exception e) {
			throw e;
		}
	}
	
	public Keys UnsuscribeKeys(String key)throws Exception {
		try{
			Keys storedKey = getKeyByKey(key).getEntity();
			pdClient.deleteKey(storedKey);
			storedKey.setPermanentlyValid(false);
			pdClient.saveApiKey(storedKey);
			return storedKey;
		}catch (Exception e) {
			throw e;
		}
	}
	
	public Keys nextKey() throws PlayerDataDBException {
		try {
			return pdClient.getValidKey();
		} catch (Exception e) {
			throw e;
		}
	}
	
	public GenericResponse<Keys> getKeyByMail(String mail) throws Exception{
		GenericResponse <Keys> response = new GenericResponse<>();
		try {
			List<Keys> userKeys = pdClient.getKeysByMail(mail);
			response.setCODE(Constants.CODE_OK);
			response.setDescription("OK");
			response.setEntityList(userKeys);
			return response;
		} catch (Exception e) {
			throw new ApiKeyManagementException("Error al buscar las api keys guardadas por el usuario");
		}
	}
	
	public GenericResponse<Keys> getKeyByKey(String key) throws Exception {
		String uncryptedKey;
		String keyHash;
		Keys storedKey;
		GenericResponse <Keys> response = new GenericResponse<>();
		try {
			keyHash = crypt.hashKey(key);
		} catch (Exception e) {
			throw new ApiKeyManagementException("Error al encriptar la clave de conexion con la api aportada");
		}
		try {
			storedKey = pdClient.getKeyByHash(keyHash);
			if (storedKey==null)
				throw new ApiKeyManagementException("Api Key no encontrada");
			else {
				uncryptedKey = crypt.decrypt(storedKey.getValor());
				if (!uncryptedKey.equals(key)) {
					throw new ApiKeyManagementException("Error al desencriptar la contraseña ya que no coincide con apiKey");
				}
				storedKey.setValor(uncryptedKey);
			}
		} catch (Exception e) {
			throw e;
		}
		response.setCODE(Constants.CODE_OK);
		response.setDescription("OK");
		response.setEntity(storedKey);
		return response;
	}

	public GenericResponse storeKey(String apiKey, String mail, String plan, int id_service) throws Exception {
		GenericResponse response = new GenericResponse();
		Keys newKey = new Keys();
		boolean isPlanValid = true;
		String cryptedKey;
		try {
			cryptedKey = crypt.encrypt(apiKey);
		} catch (Exception e) {
			throw new ApiKeyManagementException("Error al encriptar la clave de conexion con la api aportada");
		}
		newKey.setValor(cryptedKey);
		newKey.setMail(mail);
		newKey.setIdService(id_service);
		newKey.setHashKey(crypt.hashKey(apiKey));
		switch (plan) {
			case Constants.APISPORTS_FREE:
				newKey.setTotalUses(Constants.USAGES_APISPORTS_FREE);
				break;
			case Constants.APISPORTS_MEGA:
				newKey.setTotalUses(Constants.USAGES_APISPORTS_MEGA);
				break;
			case Constants.APISPORTS_PRO:
				newKey.setTotalUses(Constants.USAGES_APISPORTS_PRO);
				break;
			case Constants.APISPORTS_ULTRA:
				newKey.setTotalUses(Constants.USAGES_APISPORTS_ULTRA);
				break;
			default:
				newKey.setTotalUses(Constants.USAGES_APISPORTS_FREE);
				isPlanValid= false;
		}
		newKey.setTodayUses(0);
		newKey.setValid(true);
		newKey.setPermanentlyValid(true);
		try {
			pdClient.saveApiKey(newKey);
		} catch (Exception e) {
			throw e;
		}
		response.setCODE(Constants.CODE_OK);
		if (isPlanValid)
			response.setDescription("OK");
		else
			response.setDescription("Key guardada, pero el plan indicado no esta registrado, el sistema lo utilizara como una key gratuita (100 llamadas al dia disponibles)");
		
		return response;	
			
	}
	
	public List<Keys> getAllKeys() throws PlayerDataDBException{
		try {
			List<Keys> keys = pdClient.getAllKeys();
			if (keys==null)
				return new ArrayList<Keys>();
			return keys;
		} catch (Exception e) {
			throw e;
		}
	}
	
	public boolean checkReadiness(Keys key) {
		if (!key.isPermanentlyValid() && !key.isValid())
			return false;
		if (key.getTotalUses() < key.getTodayUses())
			return false;
		return true;
	}
	
	public Keys uncryptKey(Keys key) throws Exception {
		String keyValue = crypt.decrypt(key.getValor());
		key.setValor(keyValue);
		return key;
	}
	
	
		
}
	
