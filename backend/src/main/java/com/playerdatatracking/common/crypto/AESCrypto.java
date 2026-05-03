package com.playerdatatracking.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.playerdatatracking.common.Constants;
import com.playerdatatracking.exceptions.apikeys.SecretKeyBadGeneratedException;
import com.playerdatatracking.responses.GenericResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class AESCrypto {
	private static String algorithm = "AES/CBC/PKCS5Padding";

	@Autowired
	private Environment env;

	public void setEnv(Environment extenv) {
		this.env = extenv;
	}
	
    // Método para generar hash de la clave
    public String hashKey(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
	
	
    // Generar una clave AES
    public SecretKey generateKey(int n) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(n);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }

    // Obtener un vector de inicialización (IV)
    public IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    // Convertir la clave en String para almacenamiento
    public String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // Convertir el String en clave para uso
    public SecretKey decodeKey(String keyStr) {
        byte[] decodedKey = Base64.getDecoder().decode(keyStr);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    // Encriptar
    public String encrypt(String input) throws Exception {
        IvParameterSpec iv = generateIv();
        SecretKey key = decodeKey(env.getProperty("apikeys.secret"));
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] cipherText = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        // Concatenar IV y texto cifrado y codificar en Base64
        byte[] ivAndCipherText = new byte[iv.getIV().length + cipherText.length];
        System.arraycopy(iv.getIV(), 0, ivAndCipherText, 0, iv.getIV().length);
        System.arraycopy(cipherText, 0, ivAndCipherText, iv.getIV().length, cipherText.length);
        return Base64.getEncoder().encodeToString(ivAndCipherText);
    }

    // Desencriptar
    public String decrypt(String cipherText) throws Exception {
        byte[] decodedCipherText = Base64.getDecoder().decode(cipherText);
        IvParameterSpec iv = new IvParameterSpec(decodedCipherText, 0, 16);
        SecretKey key = decodeKey(env.getProperty("apikeys.secret"));
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] plainText = cipher.doFinal(decodedCipherText, 16, decodedCipherText.length - 16);
        return new String(plainText, StandardCharsets.UTF_8);
    }
    
    
    
    public GenericResponse getNewSecretKey() throws Exception {
    	try {
	    	GenericResponse response = new GenericResponse();
	    	SecretKey key = generateKey(256);
	    	response.setCODE(Constants.CODE_OK);
	    	response.setDescription(encodeKey(key));
	    	return response;
    	} catch (Exception e) {
    		throw new SecretKeyBadGeneratedException(e.getMessage(), e.getCause());
    	}
    }
}
