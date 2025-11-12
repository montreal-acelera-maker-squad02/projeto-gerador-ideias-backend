package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void shouldReturnNullWhenEncryptingNullIp() {
        String encrypted = ipEncryptionService.encryptIp(null);
        assertNull(encrypted);
    }

    @Test
    void shouldReturnNullWhenEncryptingBlankIp() {
        String encrypted = ipEncryptionService.encryptIp("");
        assertNull(encrypted);
    }

    @Test
    void shouldReturnNullWhenEncryptingUnknownIp() {
        String encrypted = ipEncryptionService.encryptIp("unknown");
        assertNull(encrypted);
    }

    @Test
    void shouldReturnUnknownWhenDecryptingNull() {
        String decrypted = ipEncryptionService.decryptIp(null);
        assertEquals("unknown", decrypted);
    }

    @Test
    void shouldReturnUnknownWhenDecryptingBlank() {
        String decrypted = ipEncryptionService.decryptIp("");
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
}

