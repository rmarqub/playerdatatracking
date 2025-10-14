package com.playerdatatracking.common.crypto;


import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
public class CryptoRSAService {
	
	private final RsaKeyProvider keys;

    public CryptoRSAService(RsaKeyProvider keys) {
        this.keys = keys;
    }

    public String decryptPasswordB64(String b64Cipher) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(b64Cipher);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, keys.getPrivateKey(), oaepParams);
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }

}
