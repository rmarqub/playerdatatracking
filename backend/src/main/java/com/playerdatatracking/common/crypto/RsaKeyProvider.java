package com.playerdatatracking.common.crypto;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.*;
import java.util.Base64;
import java.util.UUID;

@Component
public class RsaKeyProvider {
	
	private KeyPair keyPair;
    private String kid; // key id para rotación simple
    
    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        this.keyPair = kpg.generateKeyPair();
        this.kid = UUID.randomUUID().toString();
    }
    
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String getKid() {
        return kid;
    }

    public String exportPublicKeyPEM() {
        byte[] spki = keyPair.getPublic().getEncoded(); // X.509 SubjectPublicKeyInfo (DER)
        String b64 = Base64.getEncoder().encodeToString(spki);
        return "-----BEGIN PUBLIC KEY-----\n" +
                b64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

}
