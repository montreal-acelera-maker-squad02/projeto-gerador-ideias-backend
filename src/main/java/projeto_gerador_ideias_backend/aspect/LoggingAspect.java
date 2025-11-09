package projeto_gerador_ideias_backend.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.service.UserStatisticsService;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);
    private final Counter ideasGeneratedCounter;
    private final MeterRegistry meterRegistry;
    private final UserStatisticsService userStatisticsService;

    public LoggingAspect(MeterRegistry meterRegistry, UserStatisticsService userStatisticsService) {
        this.meterRegistry = meterRegistry;
        this.userStatisticsService = userStatisticsService;
        this.ideasGeneratedCounter = Counter.builder("ideas.generated.count")
                .description("Número total de ideias geradas com sucesso.")
                .register(meterRegistry);
    }

    @Pointcut("execution(public * projeto_gerador_ideias_backend.service.IdeaService.generate*(..))")
    public void ideaGenerationPointcut() {}

    @Around("ideaGenerationPointcut()")
    public Object logIdeaGeneration(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        String context = "N/A";

        if (args.length > 0 && args[0] instanceof IdeaRequest) {
            context = ((IdeaRequest) args[0]).getContext();
        } else if (methodName.equals("generateSurpriseIdea")) {
            context = "Surprise Me!";
        }

        log.info(">> Iniciando: {}() | Contexto: '{}'", methodName, context);
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable thrownException = null;
        String status = "success";

        try {
            result = joinPoint.proceed();

            if (result instanceof IdeaResponse response) {
                if (!response.getContent().contains("Desculpe, não posso gerar ideias")) {
                    this.ideasGeneratedCounter.increment();

                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                        String userEmail = auth.getName();
                        userStatisticsService.incrementUserIdeaCount(userEmail);
                    }
                }
            }
            return result;
        } catch (Throwable e) {
            thrownException = e;
            status = "failure";
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            if (thrownException == null) {
                log.info("<< Finalizado com sucesso: {}() | Tempo de Execução Total: {}ms", methodName, executionTime);
            } else {
                log.error("<< Finalizado com erro: {}() | Tempo de Execução Total: {}ms | Erro: {}", methodName, executionTime, thrownException.getMessage());
            }

            meterRegistry.timer("ideas.generation.time", "method", methodName, "status", status)
                    .record(executionTime, TimeUnit.MILLISECONDS);
        }
    }
}