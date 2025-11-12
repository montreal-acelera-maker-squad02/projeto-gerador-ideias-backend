package projeto_gerador_ideias_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
@Slf4j
public class IpEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Value("${ip.encryption.key:default-encryption-key-change-in-production-32-chars}")
    private String encryptionKey;

    public String encryptIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if ("unknown".equalsIgnoreCase(ip)) {
            return null;
        }

        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Erro ao criptografar IP: {}", ip, e);
            return null;
        }
    }

    public String decryptIp(String encryptedIp) {
        if (encryptedIp == null || encryptedIp.isBlank()) {
            return "unknown";
        }

        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedIp));
            String decryptedIp = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            if ("0:0:0:0:0:0:0:1".equals(decryptedIp) || "::1".equals(decryptedIp)) {
                return "127.0.0.1";
            }
            
            return decryptedIp;
        } catch (Exception e) {
            log.error("Erro ao descriptografar IP", e);
            return "unknown";
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

