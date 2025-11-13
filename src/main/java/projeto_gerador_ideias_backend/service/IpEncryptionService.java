package projeto_gerador_ideias_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
@Slf4j
public class IpEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String UNKNOWN_IP = "unknown";

    @Value("${ip.encryption.key:default-encryption-key-change-in-production-32-chars}")
    private String encryptionKey;

    public String encryptIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (UNKNOWN_IP.equalsIgnoreCase(ip)) {
            return null;
        }

        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedBytes = cipher.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            
            byte[] ivAndCiphertext = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, ivAndCiphertext, GCM_IV_LENGTH, encryptedBytes.length);
            
            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (Exception e) {
            log.error("Erro ao criptografar IP: {}", ip, e);
            return null;
        }
    }

    public String decryptIp(String encryptedIp) {
        if (encryptedIp == null || encryptedIp.isBlank()) {
            return UNKNOWN_IP;
        }

        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(encryptedIp);
            
            if (ivAndCiphertext.length < GCM_IV_LENGTH) {
                log.error("Texto cifrado muito curto para conter IV");
                return UNKNOWN_IP;
            }
            
            byte[] iv = Arrays.copyOfRange(ivAndCiphertext, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, GCM_IV_LENGTH, ivAndCiphertext.length);
            
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            String decryptedIp = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            if ("0:0:0:0:0:0:0:1".equals(decryptedIp) || "::1".equals(decryptedIp)) {
                return "127.0.0.1";
            }
            
            return decryptedIp;
        } catch (Exception e) {
            log.error("Erro ao descriptografar IP", e);
            return UNKNOWN_IP;
        }
    }

    private SecretKeySpec getSecretKey() {
        try {
            byte[] key = encryptionKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {
            log.error("Erro ao gerar chave de criptografia", e);
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 16) {
                byte[] padded = new byte[16];
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
                return new SecretKeySpec(padded, ALGORITHM);
            }
            return new SecretKeySpec(Arrays.copyOf(keyBytes, 16), ALGORITHM);
        }
    }
}

