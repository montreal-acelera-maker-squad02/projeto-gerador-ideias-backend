package projeto_gerador_ideias_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.dto.response.IdeaSummaryResponse;

import java.util.List;

@Service
@Slf4j
public class IdeasSummaryCacheService {

    private static final String IDEAS_SUMMARY_CACHE_NAME = "ideasSummaryCache";

    private final CacheManager cacheManager;
    private final projeto_gerador_ideias_backend.repository.IdeaRepository ideaRepository;
    private final projeto_gerador_ideias_backend.service.IdeaSummaryService ideaSummaryService;
    private final IdeasSummaryCacheService self;

    public IdeasSummaryCacheService(
            CacheManager cacheManager,
            projeto_gerador_ideias_backend.repository.IdeaRepository ideaRepository,
            projeto_gerador_ideias_backend.service.IdeaSummaryService ideaSummaryService,
            @Lazy IdeasSummaryCacheService self) {
        this.cacheManager = cacheManager;
        this.ideaRepository = ideaRepository;
        this.ideaSummaryService = ideaSummaryService;
        this.self = self;
    }

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
        
        List<IdeaSummaryResponse> summaries = loadIdeasSummaryFromDatabase(userId);
        cache.put(cacheKey, summaries);
        log.debug("Resumos de ideias carregados do banco e armazenados em cache para usuário {}", userId);
        return summaries;
    }
    
    private List<IdeaSummaryResponse> loadIdeasSummaryFromDatabase(Long userId) {
        List<Object[]> results = ideaRepository.findIdeasSummaryOnlyByUserId(userId);
        
        List<IdeaSummaryResponse> responses = new java.util.ArrayList<>();
        List<projeto_gerador_ideias_backend.model.Idea> ideasToUpdate = new java.util.ArrayList<>();
        
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String summary = row[1] != null ? (String) row[1] : "";
            String themeName = row[2] != null ? (String) row[2] : "";
            java.time.LocalDateTime createdAt = (java.time.LocalDateTime) row[3];
            
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
        
        if (!ideasToUpdate.isEmpty()) {
            saveIdeasInTransaction(ideasToUpdate);
        }
        
        return responses;
    }

    @Transactional
    public void saveIdeasInTransaction(List<projeto_gerador_ideias_backend.model.Idea> ideas) {
        ideaRepository.saveAll(ideas);
    }

    public void invalidateUserCache(Long userId) {
        String cacheKey = getCacheKey(userId);
        getCache().evict(cacheKey);
        log.debug("Cache de resumos invalidado para usuário {}", userId);
        
        self.preloadUserIdeasSummary(userId);
    }

    private String getCacheKey(Long userId) {
        return "ideas:summary:user:" + userId;
    }
}

