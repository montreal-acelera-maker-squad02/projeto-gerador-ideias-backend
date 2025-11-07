package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${admin.notification.email}")
    private String adminEmail;

    @Async
    public void sendSystemErrorNotification(String userEmail, String userName, int failureCount) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(adminEmail);
            message.setSubject(String.format("[ALERTA] %d falhas consecutivas na comunicação com a IA", failureCount));

            String text = String.format(
                "Usuário: %s (%s)\n\n" +
                "Verificar possível instabilidade no Sistema.\n\n" +
                "- Sistema de Monitoramento CriAItor",
                userName, userEmail
            );
            message.setText(text);

            mailSender.send(message);
            log.info("Email de alerta de sistema enviado para {}", adminEmail);
        } catch (Exception e) {
            log.error("Falha ao enviar email de alerta de sistema para {}: {}", adminEmail, e.getMessage());
        }
    }
}