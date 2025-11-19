package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import projeto_gerador_ideias_backend.dto.response.IdeaSummaryResponse;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.IdeaRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdeasSummaryCacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private IdeaRepository ideaRepository;

    @Mock
    private IdeaSummaryService ideaSummaryService;

    @Mock
    private Cache cache;

    private IdeasSummaryCacheService ideasSummaryCacheService;

    private Long userId;
    private Theme testTheme;

    @BeforeEach
    void setUp() {
        userId = 1L;
        testTheme = new Theme("TECNOLOGIA");
        testTheme.setId(1L);

        when(cacheManager.getCache("ideasSummaryCache")).thenReturn(cache);
        
        IdeasSummaryCacheService tempService = new IdeasSummaryCacheService(
                cacheManager,
                ideaRepository,
                ideaSummaryService,
                null
        );
        
        ideasSummaryCacheService = new IdeasSummaryCacheService(
                cacheManager,
                ideaRepository,
                ideaSummaryService,
                tempService
        );
    }

    @Test
    void shouldGetUserIdeasSummaryFromCache() {
        List<IdeaSummaryResponse> cachedSummaries = List.of(
                new IdeaSummaryResponse(1L, "Resumo 1", "TECNOLOGIA", LocalDateTime.now().toString())
        );

        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(cachedSummaries);
        when(cache.get("ideas:summary:user:1")).thenReturn(wrapper);

        List<IdeaSummaryResponse> result = ideasSummaryCacheService.getUserIdeasSummary(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Resumo 1", result.get(0).getSummary());
        verify(cache, times(1)).get("ideas:summary:user:1");
        verify(ideaRepository, never()).findIdeasSummaryOnlyByUserId(anyLong());
    }

    @Test
    void shouldLoadFromDatabaseWhenCacheMiss() {
        Object[] row = new Object[]{1L, "Resumo da ideia", "TECNOLOGIA", LocalDateTime.now()};
        when(cache.get("ideas:summary:user:1")).thenReturn(null);
        when(ideaRepository.findIdeasSummaryOnlyByUserId(userId)).thenReturn(Collections.singletonList(row));

        List<IdeaSummaryResponse> result = ideasSummaryCacheService.getUserIdeasSummary(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Resumo da ideia", result.get(0).getSummary());
        verify(ideaRepository, times(1)).findIdeasSummaryOnlyByUserId(userId);
        verify(cache, times(1)).put(anyString(), anyList());
    }

    @Test
    void shouldGenerateSummaryWhenMissing() {
        Idea idea = new Idea();
        idea.setId(1L);
        idea.setTheme(testTheme);
        idea.setGeneratedContent("Conteúdo completo da ideia que precisa ser resumido");
        idea.setCreatedAt(LocalDateTime.now());

        Object[] row = new Object[]{1L, null, "TECNOLOGIA", LocalDateTime.now()};
        when(cache.get("ideas:summary:user:1")).thenReturn(null);
        when(ideaRepository.findIdeasSummaryOnlyByUserId(userId)).thenReturn(Collections.singletonList(row));
        when(ideaRepository.findById(1L)).thenReturn(Optional.of(idea));
        when(ideaSummaryService.summarizeIdeaSimple(idea.getGeneratedContent())).thenReturn("Resumo gerado");

        List<IdeaSummaryResponse> result = ideasSummaryCacheService.getUserIdeasSummary(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Resumo gerado", result.get(0).getSummary());
        verify(ideaSummaryService, times(1)).summarizeIdeaSimple(idea.getGeneratedContent());
        verify(ideaRepository, times(1)).saveAll(anyList());
    }

    @Test
    void shouldInvalidateUserCache() {
        ideasSummaryCacheService.invalidateUserCache(userId);

        verify(cache, times(1)).evict("ideas:summary:user:1");
    }

    @Test
    void shouldPreloadUserIdeasSummary() {
        Object[] row = new Object[]{1L, "Resumo", "TECNOLOGIA", LocalDateTime.now()};
        when(ideaRepository.findIdeasSummaryOnlyByUserId(userId)).thenReturn(Collections.singletonList(row));

        ideasSummaryCacheService.preloadUserIdeasSummary(userId);

        verify(ideaRepository, times(1)).findIdeasSummaryOnlyByUserId(userId);
        verify(cache, times(1)).put(anyString(), anyList());
    }

    @Test
    void shouldHandleEmptyIdeasList() {
        when(cache.get("ideas:summary:user:1")).thenReturn(null);
        when(ideaRepository.findIdeasSummaryOnlyByUserId(userId)).thenReturn(Collections.emptyList());

        List<IdeaSummaryResponse> result = ideasSummaryCacheService.getUserIdeasSummary(userId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(cache, times(1)).put("ideas:summary:user:1", Collections.emptyList());
    }

    @Test
    void shouldHandleMultipleIdeas() {
        Object[] row1 = new Object[]{1L, "Resumo 1", "TECNOLOGIA", LocalDateTime.now()};
        Object[] row2 = new Object[]{2L, "Resumo 2", "TRABALHO", LocalDateTime.now()};
        when(cache.get("ideas:summary:user:1")).thenReturn(null);
        when(ideaRepository.findIdeasSummaryOnlyByUserId(userId)).thenReturn(List.of(row1, row2));

        List<IdeaSummaryResponse> result = ideasSummaryCacheService.getUserIdeasSummary(userId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }
}


