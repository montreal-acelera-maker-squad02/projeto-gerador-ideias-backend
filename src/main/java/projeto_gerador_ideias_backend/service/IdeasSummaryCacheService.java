package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.response.IdeaSummaryResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdeasSummaryCacheService {

    private static final String IDEAS_SUMMARY_CACHE_NAME = "ideasSummaryCache";

    private final CacheManager cacheManager;
    private final projeto_gerador_ideias_backend.repository.IdeaRepository ideaRepository;
    private final projeto_gerador_ideias_backend.service.IdeaSummaryService ideaSummaryService;

    private Cache getCache() {
        Cache cache = cacheManager.getCache(IDEAS_SUMMARY_CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache '" + IDEAS_SUMMARY_CACHE_NAME + "' não encontrado. Verifique a propriedade 'spring.cache.cache-names' em application.properties.");
        }
        return cache;
    }

    @Async
    public void preloadUserIdeasSummary(Long userId) {
        try {
            String cacheKey = getCacheKey(userId);
            List<IdeaSummaryResponse> summaries = loadIdeasSummaryFromDatabase(userId);
            getCache().put(cacheKey, summaries);
            log.debug("Resumos de ideias pré-carregados para usuário {}", userId);
        } catch (Exception e) {
            log.warn("Erro ao pré-carregar resumos de ideias para usuário {}", userId, e);
        }
    }

    public List<IdeaSummaryResponse> getUserIdeasSummary(Long userId) {
        String cacheKey = getCacheKey(userId);
        Cache cache = getCache();
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        
        if (wrapper != null && wrapper.get() != null) {
            @SuppressWarnings("unchecked")
            List<IdeaSummaryResponse> cached = (List<IdeaSummaryResponse>) wrapper.get();
            log.debug("Resumos de ideias retornados do cache para usuário {}", userId);
            return cached;
        }
        
        // Se não está em cache, carrega e armazena
        List<IdeaSummaryResponse> summaries = loadIdeasSummaryFromDatabase(userId);
        cache.put(cacheKey, summaries);
        log.debug("Resumos de ideias carregados do banco e armazenados em cache para usuário {}", userId);
        return summaries;
    }
    
    /**
     * Carrega os resumos diretamente do banco (sem usar ChatService para evitar dependência circular)
     */
    private List<IdeaSummaryResponse> loadIdeasSummaryFromDatabase(Long userId) {
        List<Object[]> results = ideaRepository.findIdeasSummaryOnlyByUserId(userId);
        
        List<IdeaSummaryResponse> responses = new java.util.ArrayList<>();
        List<projeto_gerador_ideias_backend.model.Idea> ideasToUpdate = new java.util.ArrayList<>();
        
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String summary = row[1] != null ? (String) row[1] : "";
            String themeName = row[2] != null ? (String) row[2] : "";
            java.time.LocalDateTime createdAt = (java.time.LocalDateTime) row[3];
            
            // Se não tem resumo, precisa carregar a ideia completa para gerar
            if (summary == null || summary.isBlank()) {
                projeto_gerador_ideias_backend.model.Idea idea = ideaRepository.findById(id).orElse(null);
                if (idea != null) {
                    summary = ideaSummaryService.summarizeIdeaSimple(idea.getGeneratedContent());
                    idea.setSummary(summary);
                    ideasToUpdate.add(idea);
                } else {
                    summary = "Sem resumo disponível";
                }
            }
            
            responses.add(new IdeaSummaryResponse(
                    id,
                    summary,
                    themeName,
                    createdAt.toString()
            ));
        }
        
        // Salva os resumos gerados em batch (se houver)
        if (!ideasToUpdate.isEmpty()) {
            saveIdeasSummary(ideasToUpdate);
        }
        
        return responses;
    }
    
    @Transactional
    private void saveIdeasSummary(List<projeto_gerador_ideias_backend.model.Idea> ideas) {
        ideaRepository.saveAll(ideas);
    }

    public void invalidateUserCache(Long userId) {
        String cacheKey = getCacheKey(userId);
        getCache().evict(cacheKey);
        log.debug("Cache de resumos invalidado para usuário {}", userId);
        
        preloadUserIdeasSummary(userId);
    }

    private String getCacheKey(Long userId) {
        return "ideas:summary:user:" + userId;
    }
}

