package io.github.eschoe.llmragapi.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HashUtil {

    public String sha256(String... input) {

        byte[] hash;

        try {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String s : input) { md.update(s.getBytes(StandardCharsets.UTF_8)); }
            hash = md.digest();


        } catch( NoSuchAlgorithmException e ) {
            hash = null;
        }

        return HexFormat.of().formatHex(hash);

    }

}
