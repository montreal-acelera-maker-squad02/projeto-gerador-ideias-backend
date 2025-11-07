package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private static final String FROM_EMAIL = "sistema@criaitor.com";
    private static final String ADMIN_EMAIL = "admin@criaitor.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "adminEmail", ADMIN_EMAIL);
    }

    @Test
    void deveCriarEmailComInformacoesCorretas() {
        String userEmail = "usuario@teste.com";
        String userName = "João Silva";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertEquals(FROM_EMAIL, sentMessage.getFrom());
        assertEquals(ADMIN_EMAIL, sentMessage.getTo()[0]);
        assertEquals("[ALERTA] 4 falhas consecutivas na comunicação com a IA", sentMessage.getSubject());

        String expectedText = String.format(
                "Usuário: %s (%s)\n\n" +
                        "Verificar possível instabilidade no Sistema.\n\n" +
                        "- Sistema de Monitoramento CriAItor",
                userName, userEmail
        );
        assertEquals(expectedText, sentMessage.getText());
    }

    @Test
    void deveEnviarEmailComDiferentesContagensDeFalhas() {
        String userEmail = "usuario@teste.com";
        String userName = "Maria Santos";
        int failureCount = 8;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("[ALERTA] 8 falhas consecutivas na comunicação com a IA", sentMessage.getSubject());
    }

    @Test
    void deveConterEmailDoUsuarioNoCorpoMensagem() {
        String userEmail = "teste@exemplo.com";
        String userName = "Teste User";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.getText().contains(userEmail));
        assertTrue(sentMessage.getText().contains(userName));
    }

    @Test
    void deveConterMensagemDeMonitoramentoNoEmail() {
        String userEmail = "usuario@teste.com";
        String userName = "Teste";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.getText().contains("Verificar possível instabilidade no Sistema"));
        assertTrue(sentMessage.getText().contains("Sistema de Monitoramento CriAItor"));
    }

    @Test
    void naoDeveLancarExcecaoQuandoEnvioFalhar() {
        String userEmail = "usuario@teste.com";
        String userName = "Teste";
        int failureCount = 4;

        doThrow(new RuntimeException("Erro ao enviar email"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                emailService.sendSystemErrorNotification(userEmail, userName, failureCount)
        );

        await();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void deveEnviarEmailParaAdminConfigurado() {
        String userEmail = "usuario@teste.com";
        String userName = "Teste";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertArrayEquals(new String[]{ADMIN_EMAIL}, sentMessage.getTo());
    }

    @Test
    void deveUsarEmailRemetenteConfigurado() {
        String userEmail = "usuario@teste.com";
        String userName = "Teste";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals(FROM_EMAIL, sentMessage.getFrom());
    }

    @Test
    void deveFormatarAssuntoComContagemCorreta() {
        String userEmail = "usuario@teste.com";
        String userName = "Teste";
        int failureCount = 12;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.getSubject().contains("12 falhas"));
    }

    @Test
    void deveEnviarEmailComNomeUsuarioEspecial() {
        String userEmail = "usuario@teste.com";
        String userName = "José da Silva Júnior";
        int failureCount = 4;

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSystemErrorNotification(userEmail, userName, failureCount);

        await();

        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertTrue(sentMessage.getText().contains(userName));
    }

    private void await() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}