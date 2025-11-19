package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailureCounterServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private EmailService emailService;

    private FailureCounterService failureCounterService;

    private Cache cache;

    private static final String USER_EMAIL = "usuario@teste.com";
    private static final String USER_NAME = "João Silva";

    @BeforeEach
    void setUp() {
        cache = new ConcurrentMapCache("consecutiveFailureCache");
        when(cacheManager.getCache("consecutiveFailureCache")).thenReturn(cache);
        failureCounterService = new FailureCounterService(cacheManager, emailService);
    }

    @Test
    void deveIncrementarContadorNaPrimeiraFalha() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper valueWrapper = cache.get(USER_EMAIL);
        assertNotNull(valueWrapper);
        assertEquals(1, valueWrapper.get());
        verify(emailService, never()).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void deveIncrementarContadorEmFalhasConsecutivas() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper valueWrapper = cache.get(USER_EMAIL);
        assertNotNull(valueWrapper);
        assertEquals(3, valueWrapper.get());
        verify(emailService, never()).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void deveEnviarEmailAposQuartaFalha() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        verify(emailService, times(1)).sendSystemErrorNotification(USER_EMAIL, USER_NAME, 4);
    }

    @Test
    void deveResetarContadorAposQuartaFalha() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper valueWrapper = cache.get(USER_EMAIL);
        assertNull(valueWrapper);
    }

    @Test
    void deveRecomecarContagemAposReset() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper valueWrapper = cache.get(USER_EMAIL);
        assertNotNull(valueWrapper);
        assertEquals(2, valueWrapper.get());

        verify(emailService, times(1)).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void deveEnviarEmailNovamenteAposNovaSequenciaDeQuatroFalhas() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        verify(emailService, times(2)).sendSystemErrorNotification(USER_EMAIL, USER_NAME, 4);
    }

    @Test
    void deveManterContadoresSeparadosParaDiferentesUsuarios() {
        String user1Email = "usuario1@teste.com";
        String user1Name = "Usuário 1";
        String user2Email = "usuario2@teste.com";
        String user2Name = "Usuário 2";

        failureCounterService.handleFailure(user1Email, user1Name);
        failureCounterService.handleFailure(user1Email, user1Name);
        failureCounterService.handleFailure(user2Email, user2Name);

        Cache.ValueWrapper user1Value = cache.get(user1Email);
        Cache.ValueWrapper user2Value = cache.get(user2Email);

        assertNotNull(user1Value);
        assertNotNull(user2Value);
        assertEquals(2, user1Value.get());
        assertEquals(1, user2Value.get());
    }

    @Test
    void deveResetarContadorManualmente() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper beforeReset = cache.get(USER_EMAIL);
        assertNotNull(beforeReset);
        assertEquals(2, beforeReset.get());

        failureCounterService.resetCounter(USER_EMAIL);

        Cache.ValueWrapper afterReset = cache.get(USER_EMAIL);
        assertNull(afterReset);
    }

    @Test
    void naoDeveEnviarEmailAntesDaQuartaFalha() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        verify(emailService, never()).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void deveLancarExcecaoQuandoCacheNaoEncontrado() {
        when(cacheManager.getCache("consecutiveFailureCache")).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new FailureCounterService(cacheManager, emailService)
        );

        assertTrue(exception.getMessage().contains("Cache 'consecutiveFailureCache' não encontrado"));
    }

    @Test
    void devePermitirResetarContadorDeUsuarioSemFalhas() {
        assertDoesNotThrow(() -> failureCounterService.resetCounter("usuario.nao.existente@teste.com"));
    }

    @Test
    void deveEnviarEmailComContagemCorreta() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        verify(emailService).sendSystemErrorNotification(
                USER_EMAIL,
                USER_NAME,
                4
        );
    }

    @Test
    void deveManterIntegridadeDoContadorEmConcorrencia() throws InterruptedException {
        String userEmail = "concurrent@teste.com";
        String userName = "Concurrent User";

        Thread thread1 = new Thread(() -> failureCounterService.handleFailure(userEmail, userName));
        Thread thread2 = new Thread(() -> failureCounterService.handleFailure(userEmail, userName));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        Cache.ValueWrapper valueWrapper = cache.get(userEmail);
        assertNotNull(valueWrapper);
        assertEquals(2, valueWrapper.get());
    }

    @Test
    void deveIniciarContadorEmZeroParaNovoUsuario() {
        Cache.ValueWrapper before = cache.get(USER_EMAIL);
        assertNull(before);

        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);

        Cache.ValueWrapper after = cache.get(USER_EMAIL);
        assertNotNull(after);
        assertEquals(1, after.get());
    }

    @Test
    void deveIncrementarContadorCorretamenteQuandoValueWrapperExiste() {
        cache.put(USER_EMAIL, 2);
        
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        
        Cache.ValueWrapper valueWrapper = cache.get(USER_EMAIL);
        assertNotNull(valueWrapper);
        assertEquals(3, valueWrapper.get());
        verify(emailService, never()).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void naoDeveEnviarEmailQuandoContadorNaoAtingeThreshold() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        
        verify(emailService, never()).sendSystemErrorNotification(anyString(), anyString(), anyInt());
    }

    @Test
    void deveResetarContadorApenasQuandoThresholdEAtingido() {
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        
        Cache.ValueWrapper before = cache.get(USER_EMAIL);
        assertNotNull(before);
        assertEquals(3, before.get());
        
        failureCounterService.handleFailure(USER_EMAIL, USER_NAME);
        
        Cache.ValueWrapper after = cache.get(USER_EMAIL);
        assertNull(after);
        verify(emailService, times(1)).sendSystemErrorNotification(USER_EMAIL, USER_NAME, 4);
    }
}