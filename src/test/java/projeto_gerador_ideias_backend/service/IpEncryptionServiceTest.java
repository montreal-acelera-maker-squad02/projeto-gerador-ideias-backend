package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class IpEncryptionServiceTest {

    @Autowired
    private IpEncryptionService ipEncryptionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ipEncryptionService, "encryptionKey", "test-encryption-key-32-chars-long");
    }

    @Test
    void shouldEncryptIpSuccessfully() {
        String ip = "192.168.1.1";
        String encrypted = ipEncryptionService.encryptIp(ip);
        
        assertNotNull(encrypted);
        assertNotEquals(ip, encrypted);
        assertTrue(encrypted.length() > 0);
    }

    @Test
    void shouldDecryptIpSuccessfully() {
        String ip = "192.168.1.1";
        String encrypted = ipEncryptionService.encryptIp(ip);
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        
        assertEquals(ip, decrypted);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "UNKNOWN", "Unknown"})
    void shouldReturnNullWhenEncryptingInvalidIp(String ip) {
        String encrypted = ipEncryptionService.encryptIp(ip);
        assertNull(encrypted);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReturnUnknownWhenDecryptingInvalidInput(String invalidInput) {
        String decrypted = ipEncryptionService.decryptIp(invalidInput);
        assertEquals("unknown", decrypted);
    }

    @Test
    void shouldEncryptAndDecryptIpv6() {
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        String encrypted = ipEncryptionService.encryptIp(ipv6);
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        
        assertEquals(ipv6, decrypted);
    }

    @Test
    void shouldEncryptAndDecryptDifferentIps() {
        String[] ips = {"192.168.1.1", "10.0.0.1", "172.16.0.1", "127.0.0.1"};
        
        for (String ip : ips) {
            String encrypted = ipEncryptionService.encryptIp(ip);
            String decrypted = ipEncryptionService.decryptIp(encrypted);
            assertEquals(ip, decrypted, "Falha ao criptografar/descriptografar: " + ip);
        }
    }

    @Test
    void shouldDecryptIpv6Localhost() {
        String ipv6Localhost = "::1";
        String encrypted = ipEncryptionService.encryptIp(ipv6Localhost);
        assertNotNull(encrypted);
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        assertEquals("127.0.0.1", decrypted);
        assertNotEquals(ipv6Localhost, decrypted);
    }

    @Test
    void shouldReturnUnknownWhenDecryptingInvalidBase64() {
        String decrypted = ipEncryptionService.decryptIp("invalid-base64!!!");
        assertEquals("unknown", decrypted);
    }

    @Test
    void shouldReturnUnknownWhenDecryptingTooShortCiphertext() {
        String shortCiphertext = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String decrypted = ipEncryptionService.decryptIp(shortCiphertext);
        assertEquals("unknown", decrypted);
    }

    @Test
    void shouldHandleEncryptionKeyShorterThan16Bytes() {
        ReflectionTestUtils.setField(ipEncryptionService, "encryptionKey", "short");
        
        String ip = "192.168.1.1";
        String encrypted = ipEncryptionService.encryptIp(ip);
        assertNotNull(encrypted);
        
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        assertEquals(ip, decrypted);
    }

    @Test
    void shouldHandleGetSecretKeyException() {
        ReflectionTestUtils.setField(ipEncryptionService, "encryptionKey", null);
        
        String ip = "192.168.1.1";
        String encrypted = ipEncryptionService.encryptIp(ip);
        
        assertNull(encrypted);
    }

    @Test
    void shouldDecryptIpv6LocalhostFromEncrypted() {
        String ipv6Localhost = "0:0:0:0:0:0:0:1";
        String encrypted = ipEncryptionService.encryptIp(ipv6Localhost);
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        assertEquals("127.0.0.1", decrypted);
    }

    @Test
    void shouldDecryptIpv6LocalhostShortFromEncrypted() {
        String ipv6LocalhostShort = "::1";
        String encrypted = ipEncryptionService.encryptIp(ipv6LocalhostShort);
        String decrypted = ipEncryptionService.decryptIp(encrypted);
        assertEquals("127.0.0.1", decrypted);
    }
}

