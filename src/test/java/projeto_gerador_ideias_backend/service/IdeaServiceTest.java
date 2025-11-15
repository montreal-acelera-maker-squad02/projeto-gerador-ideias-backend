package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import projeto_gerador_ideias_backend.dto.request.IdeaRequest;
import projeto_gerador_ideias_backend.dto.response.IdeaResponse;
import projeto_gerador_ideias_backend.exceptions.OllamaServiceException;
import projeto_gerador_ideias_backend.exceptions.ResourceNotFoundException;
import projeto_gerador_ideias_backend.model.Idea;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.model.User;
import projeto_gerador_ideias_backend.repository.IdeaRepository;
import projeto_gerador_ideias_backend.repository.ThemeRepository;
import projeto_gerador_ideias_backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class IdeaServiceTest {

    @Mock
    private IdeaRepository ideaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OllamaCacheableService ollamaService;

    @Mock
    private FailureCounterService failureCounterService;

    @Mock
    private ThemeRepository themeRepository;

    @Mock
    private ThemeService themeService;

    @Mock
    private IdeaSummaryService ideaSummaryService;

    @Mock
    private IdeasSummaryCacheService ideasSummaryCacheService;

    @InjectMocks
    private IdeaService ideaService;

    private User testUser;
    private Idea testIdea;
    private final String testUserEmail = "test@example.com";
    private Theme tecnologiaTheme;
    private Theme trabalhoTheme;
    private Theme estudosTheme;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setFavoriteIdeas(new HashSet<>());

        tecnologiaTheme = new Theme("TECNOLOGIA");
        tecnologiaTheme.setId(1L);

        trabalhoTheme = new Theme("TRABALHO");
        trabalhoTheme.setId(2L);

        estudosTheme = new Theme("ESTUDOS");
        estudosTheme.setId(3L);

        testIdea = new Idea(tecnologiaTheme, "Contexto", "Conteudo Gerado", "mistral", 100L);
        testIdea.setId(1L);
        testIdea.setUser(testUser);
        testIdea.setCreatedAt(LocalDateTime.now());

        ReflectionTestUtils.setField(ideaService, "ollamaModel", "mistral");

        SecurityContextHolder.clearContext();

        when(themeService.findByID(1L)).thenReturn(tecnologiaTheme);
        when(themeService.findByID(2L)).thenReturn(trabalhoTheme);
        when(themeService.findByID(3L)).thenReturn(estudosTheme);
        when(ideaSummaryService.summarizeIdeaSimple(anyString())).thenAnswer(inv -> {
            String content = inv.getArgument(0);
            if (content == null || content.length() <= 50) return content;
            return content.substring(0, Math.min(50, content.length()));
        });
        doNothing().when(ideasSummaryCacheService).invalidateUserCache(anyLong());

        when(themeRepository.findById(1L)).thenReturn(Optional.of(tecnologiaTheme));
        when(themeRepository.findById(2L)).thenReturn(Optional.of(trabalhoTheme));
        when(themeRepository.findById(3L)).thenReturn(Optional.of(estudosTheme));

        when(themeRepository.findAll()).thenReturn(List.of(tecnologiaTheme, trabalhoTheme, estudosTheme));
        when(themeService.getAll()).thenReturn(List.of(tecnologiaTheme, trabalhoTheme, estudosTheme));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        UserDetails mockUserDetails = mock(UserDetails.class);
        when(mockUserDetails.getUsername()).thenReturn(testUser.getEmail());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                mockUserDetails,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
    }

    @Test
    void generateIdea_ShouldReturnFromPersonalCache_WhenCacheExistsAndSkipCacheIsFalse() {
        setupSecurityContext();

        Theme localTecnologiaTheme = new Theme();
        localTecnologiaTheme.setId(1L);
        localTecnologiaTheme.setName("Tecnologia");

        IdeaRequest request = new IdeaRequest();
        request.setTheme(localTecnologiaTheme.getId());
        request.setContext("Contexto");

        when(themeService.findByID(localTecnologiaTheme.getId())).thenReturn(localTecnologiaTheme);

        when(ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                testUser, localTecnologiaTheme, request.getContext()))
                .thenReturn(Optional.of(testIdea));

        IdeaResponse response = ideaService.generateIdea(request, false);

        assertNotNull(response);
        assertEquals(testIdea.getGeneratedContent(), response.getContent());
        assertEquals(testUser.getName(), response.getUserName());

        verify(ollamaService, never()).getAiResponse(anyString());
        verify(ideaRepository, never()).save(any(Idea.class));
    }

    @Test
    void generateIdea_ShouldGenerateNew_WhenPersonalCacheMisses() {
        setupSecurityContext();

        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Novo Contexto");

        String aiResponse = "Nova ideia gerada pela IA";

        when(themeService.findByID(tecnologiaTheme.getId())).thenReturn(tecnologiaTheme);

        when(ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                testUser, tecnologiaTheme, request.getContext()))
                .thenReturn(Optional.empty());

        when(ollamaService.getAiResponse(contains("Analise o 'Tópico'"))).thenReturn("SEGURO");
        when(ollamaService.getAiResponse(contains("Gere uma ideia concisa"))).thenReturn(aiResponse);

        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea savedIdea = invocation.getArgument(0);
            savedIdea.setId(2L);
            savedIdea.setCreatedAt(LocalDateTime.now());
            return savedIdea;
        });

        IdeaResponse response = ideaService.generateIdea(request, false);

        assertNotNull(response);
        assertEquals(aiResponse, response.getContent());
        assertEquals(testUser.getName(), response.getUserName());
        verify(ollamaService, times(2)).getAiResponse(anyString());
        verify(ideaRepository, times(1)).save(any(Idea.class));
    }

    @Test
    void generateIdea_ShouldGenerateNew_WhenSkipCacheIsTrue() {
        setupSecurityContext();
        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Contexto");

        String aiResponse = "Nova ideia (cache ignorado)";

        when(ollamaService.getAiResponseBypassingCache(contains("Analise o 'Tópico'"))).thenReturn("SEGURO");
        when(ollamaService.getAiResponseBypassingCache(contains("Gere uma ideia concisa"))).thenReturn(aiResponse);
        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea savedIdea = invocation.getArgument(0);
            savedIdea.setId(3L);
            savedIdea.setCreatedAt(LocalDateTime.now());
            return savedIdea;
        });

        IdeaResponse response = ideaService.generateIdea(request, true);

        assertNotNull(response);
        assertEquals(aiResponse, response.getContent());
        verify(ideaRepository, never()).findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(any(), any(), any());
        verify(ollamaService, never()).getAiResponse(anyString());
        verify(ollamaService, times(2)).getAiResponseBypassingCache(anyString());
        verify(ideaRepository, times(1)).save(any(Idea.class));
    }

    @Test
    void generateIdea_ShouldReturnRejection_WhenModerationFails() {
        setupSecurityContext();

        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Contexto Perigoso");

        when(themeService.findByID(tecnologiaTheme.getId())).thenReturn(tecnologiaTheme);

        when(ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                testUser, tecnologiaTheme, request.getContext()))
                .thenReturn(Optional.empty());

        when(ollamaService.getAiResponse(contains("Analise o 'Tópico'"))).thenReturn("PERIGOSO");

        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IdeaResponse response = ideaService.generateIdea(request, false);

        assertNotNull(response);
        assertEquals("Desculpe, não posso gerar ideias sobre esse tema.", response.getContent());
        verify(ollamaService, times(1)).getAiResponse(anyString());
        verify(ollamaService, never()).getAiResponse(contains("Gere uma ideia concisa"));
        verify(ideaRepository, times(1)).save(any(Idea.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_NoFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(null, null, null, null, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveListarHistorico_ApenasComTema() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(null, 1L, null, null, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_WithDateFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(null, null, start, end, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_WithAllFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(null, 1L, start, end, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listarMinhasIdeias_ShouldReturnUserIdeas() {
        setupSecurityContext();
        Pageable pageable = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Idea> userIdeiasPage = new PageImpl<>(List.of(testIdea, testIdea), pageable, 2);
        when(ideaRepository.findByUserId(testUser.getId(), pageable)).thenReturn(userIdeiasPage);

        Page<IdeaResponse> response = ideaService.listarMinhasIdeiasPaginadas(0, 6);

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        assertEquals(testIdea.getGeneratedContent(), response.getContent().get(0).getContent());
    }

    @Test
    void listarMinhasIdeias_ShouldThrowException_WhenNoIdeasFound() {
        setupSecurityContext();
        Pageable pageable = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Idea> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(ideaRepository.findByUserId(testUser.getId(), pageable)).thenReturn(emptyPage);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.listarMinhasIdeiasPaginadas(0, 6);
        });

        assertEquals("Nenhuma ideia encontrada para o usuário: " + testUser.getEmail(), exception.getMessage());
    }

    @Test
    void generateSurpriseIdea_ShouldGenerateAndSaveSurprise() {
        setupSecurityContext();
        String aiResponse = "Slogan incrível";

        when(ollamaService.getAiResponseBypassingCache(anyString())).thenReturn(aiResponse);
        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea savedIdea = invocation.getArgument(0);
            savedIdea.setId(4L);
            savedIdea.setCreatedAt(LocalDateTime.now());
            return savedIdea;
        });

        IdeaResponse response = ideaService.generateSurpriseIdea();

        assertNotNull(response);
        assertEquals(aiResponse, response.getContent());
        assertNotNull(response.getContext());
        verify(ollamaService, times(1)).getAiResponseBypassingCache(anyString());
        verify(ideaRepository, times(1)).save(any(Idea.class));
    }

    @Test
    void favoritarIdeia_ShouldAddFavorite() {
        setupSecurityContext();
        when(ideaRepository.findById(testIdea.getId())).thenReturn(Optional.of(testIdea));
        when(userRepository.saveAndFlush(testUser)).thenReturn(testUser);

        ideaService.favoritarIdeia(testIdea.getId());

        assertTrue(testUser.getFavoriteIdeas().contains(testIdea));
        verify(userRepository, times(1)).saveAndFlush(testUser);
    }

    @Test
    void favoritarIdeia_ShouldThrowException_WhenAlreadyFavorited() {
        setupSecurityContext();
        testUser.getFavoriteIdeas().add(testIdea);
        when(ideaRepository.findById(testIdea.getId())).thenReturn(Optional.of(testIdea));

        Long ideaId = testIdea.getId();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.favoritarIdeia(ideaId);
        });

        assertEquals("Ideia já está favoritada.", exception.getMessage());
    }

    @Test
    void favoritarIdeia_ShouldThrowException_WhenIdeaNotFound() {
        setupSecurityContext();
        when(ideaRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.favoritarIdeia(99L);
        });

        assertEquals("Ideia não encontrada.", exception.getMessage());
    }

    @Test
    void desfavoritarIdeia_ShouldRemoveFavorite() {
        setupSecurityContext();
        testUser.getFavoriteIdeas().add(testIdea);
        when(ideaRepository.findById(testIdea.getId())).thenReturn(Optional.of(testIdea));
        when(userRepository.saveAndFlush(testUser)).thenReturn(testUser);

        ideaService.desfavoritarIdeia(testIdea.getId());

        assertFalse(testUser.getFavoriteIdeas().contains(testIdea));
        verify(userRepository, times(1)).saveAndFlush(testUser);
    }

    @Test
    void desfavoritarIdeia_ShouldThrowException_WhenNotFavorited() {
        setupSecurityContext();
        assertTrue(testUser.getFavoriteIdeas().isEmpty());
        when(ideaRepository.findById(testIdea.getId())).thenReturn(Optional.of(testIdea));

        Long ideaId = testIdea.getId();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.desfavoritarIdeia(ideaId);
        });

        assertEquals("Ideia não está favoritada.", exception.getMessage());
    }

    @Test
    void getCurrentAuthenticatedUser_ShouldThrowException_WhenNotAuthenticated() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarMinhasIdeiasPaginadas(0, 6);
        });

        assertEquals("Usuário não autenticado. Não é possível gerar ideias.", exception.getMessage());
    }

    @Test
    void getCurrentAuthenticatedUser_ShouldThrowException_WhenUserNotInDb() {
        setupSecurityContext();

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarMinhasIdeiasPaginadas(0, 6);
        });

        assertEquals("Usuário autenticado não encontrado no banco de dados: " + testUserEmail, exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("provideCleanUpAiResponseCases")
    void cleanUpAiResponse_ShouldHandleVariousInputs(String input, String expectedOutput) {
        String cleaned = (String) ReflectionTestUtils.invokeMethod(
                ideaService,
                "cleanUpAiResponse",
                input
        );

        assertEquals(expectedOutput, cleaned);
    }

    private static Stream<Arguments> provideCleanUpAiResponseCases() {
        return Stream.of(
                Arguments.of("### Resposta:\nConteúdo limpo", "Conteúdo limpo"),
                Arguments.of("\"Conteúdo entre aspas\"", "Conteúdo entre aspas"),
                Arguments.of("I cannot fulfill this request.", "Desculpe, não posso gerar ideias sobre esse tema."),
                Arguments.of(" ", "Desculpe, não posso gerar ideias sobre esse tema.")
        );
    }

    @Test
    void cleanUpAiResponse_ShouldFormatSurprise() {
        String input = "Ideia surpresa gerada";
        String cleaned = (String) ReflectionTestUtils.invokeMethod(ideaService, "cleanUpAiResponse", input);
        assertEquals("Ideia surpresa gerada", cleaned);
    }

    @Test
    void shouldListFavoritedIdeasSuccessfully() {
        setupSecurityContext();

        Idea idea1 = new Idea();
        idea1.setId(1L);
        idea1.setTheme(tecnologiaTheme);
        idea1.setGeneratedContent("Ideia 1");
        idea1.setExecutionTimeMs(1000L);
        idea1.setUser(testUser);
        idea1.setCreatedAt(LocalDateTime.now().minusDays(1));

        Idea idea2 = new Idea();
        idea2.setId(2L);
        idea2.setTheme(trabalhoTheme);
        idea2.setGeneratedContent("Ideia 2");
        idea2.setExecutionTimeMs(1000L);
        idea2.setUser(testUser);
        idea2.setCreatedAt(LocalDateTime.now());
        Set<Idea> favorites = new HashSet<>();
        favorites.add(idea1);
        favorites.add(idea2);
        testUser.setFavoriteIdeas(favorites);

        Page<IdeaResponse> result = ideaService.listarIdeiasFavoritadasPaginadas(0, 6);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(r -> r.getContent().equals("Ideia 1")));
        assertTrue(result.getContent().stream().anyMatch(r -> r.getContent().equals("Ideia 2")));
        assertEquals("Test User", result.getContent().get(0).getUserName());
    }

    @Test
    void shouldThrowExceptionWhenNoFavoritedIdeas() {
        setupSecurityContext();
        testUser.setFavoriteIdeas(new HashSet<>());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> ideaService.listarIdeiasFavoritadasPaginadas(0, 6));

        assertEquals("Nenhuma ideia favoritada encontrada para este usuário.", ex.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        setupSecurityContext();

        when(userRepository.findByEmail(testUserEmail)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> ideaService.listarIdeiasFavoritadasPaginadas(0, 6));

        assertEquals("Usuário autenticado não encontrado no banco de dados: " + testUserEmail, ex.getMessage());
    }

    @Test
    void generateIdea_ShouldHandleOllamaServiceException() {
        setupSecurityContext();

        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Contexto");

        when(themeService.findByID(tecnologiaTheme.getId())).thenReturn(tecnologiaTheme);

        when(ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                testUser, tecnologiaTheme, request.getContext()))
                .thenReturn(Optional.empty());

        when(ollamaService.getAiResponse(anyString()))
                .thenThrow(new OllamaServiceException("Erro na IA"));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ideaService.generateIdea(request, false);
        });

        assertNotNull(exception);
        verify(failureCounterService).handleFailure(testUser.getEmail(), testUser.getName());
        verify(failureCounterService, never()).resetCounter(anyString());
    }

    @Test
    void generateIdea_ShouldResetFailureCounterOnSuccess() {
        setupSecurityContext();

        IdeaRequest request = new IdeaRequest();
        request.setTheme(tecnologiaTheme.getId());
        request.setContext("Novo Contexto");

        String aiResponse = "Nova ideia gerada pela IA";

        when(themeService.findByID(tecnologiaTheme.getId())).thenReturn(tecnologiaTheme);

        when(ideaRepository.findFirstByUserAndThemeAndContextOrderByCreatedAtDesc(
                testUser, tecnologiaTheme, request.getContext()))
                .thenReturn(Optional.empty());

        when(ollamaService.getAiResponse(contains("Analise o 'Tópico'"))).thenReturn("SEGURO");
        when(ollamaService.getAiResponse(contains("Gere uma ideia concisa"))).thenReturn(aiResponse);

        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea savedIdea = invocation.getArgument(0);
            savedIdea.setId(2L);
            savedIdea.setCreatedAt(LocalDateTime.now());
            return savedIdea;
        });

        ideaService.generateIdea(request, false);

        verify(failureCounterService).resetCounter(testUser.getEmail());
    }


    @Test
    void generateSurpriseIdea_ShouldHandleOllamaServiceException() {
        setupSecurityContext();
        when(ollamaService.getAiResponseBypassingCache(anyString())).thenThrow(new OllamaServiceException("Erro na IA"));

        OllamaServiceException exception = assertThrows(OllamaServiceException.class, () -> {
            ideaService.generateSurpriseIdea();
        });

        assertNotNull(exception);
        verify(failureCounterService).handleFailure(testUser.getEmail(), testUser.getName());
        verify(failureCounterService, never()).resetCounter(anyString());
    }

    @Test
    void generateSurpriseIdea_ShouldResetFailureCounterOnSuccess() {
        setupSecurityContext();
        String aiResponse = "Slogan incrível";

        when(ollamaService.getAiResponseBypassingCache(anyString())).thenReturn(aiResponse);
        when(ideaRepository.save(any(Idea.class))).thenAnswer(invocation -> {
            Idea savedIdea = invocation.getArgument(0);
            savedIdea.setId(4L);
            savedIdea.setCreatedAt(LocalDateTime.now());
            return savedIdea;
        });

        ideaService.generateSurpriseIdea();

        verify(failureCounterService).resetCounter(testUser.getEmail());
    }

    @ParameterizedTest
    @MethodSource("provideCleanUpAiResponseAdditionalCases")
    void cleanUpAiResponse_ShouldHandleVariousCleanupScenarios(String input, String expectedOutput) {
        String cleaned = (String) ReflectionTestUtils.invokeMethod(
                ideaService,
                "cleanUpAiResponse",
                input
        );

        assertNotNull(cleaned);
        assertEquals(expectedOutput, cleaned);
        assertFalse(cleaned.startsWith("###") || cleaned.startsWith("####"));
    }

    private static Stream<Arguments> provideCleanUpAiResponseAdditionalCases() {
        return Stream.of(
                Arguments.of("Embora seja impossível\nConteúdo válido", "Conteúdo válido"),
                Arguments.of("Sorry, I can't fulfill this request.", "Desculpe, não posso gerar ideias sobre esse tema."),
                Arguments.of("#### Header\n### Subheader\nConteúdo real", "Conteúdo real"),
                Arguments.of("\"Conteúdo entre aspas\"", "Conteúdo entre aspas"),
                Arguments.of("\"\"", "\"\"")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_ShouldUseSpecification_WhenUserIdProvided() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);

        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(testUser.getId(), 1L, start, end, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listarHistoricoIdeiasFiltrado_ShouldThrowException_WhenInvalidTheme() {
        when(themeRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.listarHistoricoIdeiasFiltrado(null, 99L, null, null, 0, 10);
        });

        assertTrue(exception.getMessage().contains("O tema com ID '99' é inválido."));
    }

    @Test
    void listarHistoricoIdeiasFiltrado_ShouldThrowException_WhenInvalidThemeWithUserId() {
        when(themeRepository.findById(99L)).thenReturn(Optional.empty());

        Long userId = testUser.getId();
        Long invalidThemeId = 99L;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.listarHistoricoIdeiasFiltrado(userId, invalidThemeId, null, null, 0, 10);
        });

        assertTrue(exception.getMessage().contains("O tema com ID '99' é inválido."));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_ShouldThrowException_WhenEmptyList() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarHistoricoIdeiasFiltrado(null, null, null, null, 0, 10);
        });

        assertEquals("Nenhuma ideia encontrada no banco de dados para os filtros informados.", exception.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_ShouldFilterByUserIdAndTheme() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);
        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(testUser.getId(), 1L, null, null, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHistoricoIdeiasFiltrado_ShouldFilterByUserIdAndDates() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Idea> ideaPage = new PageImpl<>(List.of(testIdea), pageable, 1);

        when(ideaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(ideaPage);

        Page<IdeaResponse> response = ideaService.listarHistoricoIdeiasFiltrado(testUser.getId(), null, start, end, 0, 10);

        assertEquals(1, response.getTotalElements());
        verify(ideaRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCurrentAuthenticatedUser_ShouldThrowException_WhenPrincipalNotUserDetails() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "notUserDetails",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarMinhasIdeiasPaginadas(0, 6);
        });

        assertEquals("Usuário não autenticado. Não é possível gerar ideias.", exception.getMessage());
    }

    @Test
    void getCurrentAuthenticatedUser_ShouldThrowException_WhenAuthenticationIsNull() {
        SecurityContextHolder.clearContext();

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarMinhasIdeiasPaginadas(0, 6);
        });

        assertEquals("Usuário não autenticado. Não é possível gerar ideias.", exception.getMessage());
    }


    @Test
    void cleanUpAiResponse_ShouldFormatSurprise_WithContext() {
        String input = "Ideia surpresa";
        String cleaned = (String) ReflectionTestUtils.invokeMethod(
                ideaService,
                "cleanUpAiResponse",
                input
        );

        assertEquals("Ideia surpresa", cleaned);
    }

    @Test
    void listarIdeiasFavoritadas_ShouldThrowException_WhenFavoritesIsNull() {
        setupSecurityContext();
        testUser.setFavoriteIdeas(null);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            ideaService.listarIdeiasFavoritadasPaginadas(0, 6);
        });

        assertEquals("Nenhuma ideia favoritada encontrada para este usuário.", exception.getMessage());
    }

    @Test
    void desfavoritarIdeia_ShouldThrowException_WhenIdeaNotFound() {
        setupSecurityContext();
        when(ideaRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ideaService.desfavoritarIdeia(99L);
        });

        assertEquals("Ideia não encontrada.", exception.getMessage());
    }

    @Test
    void shouldReturnFavoriteIdeasCount() {
        // Arrange
        setupSecurityContext();
        long expectedCount = 7L;
        when(ideaRepository.countFavoriteIdeasByUserId(testUser.getId())).thenReturn(expectedCount);

        // Act
        long actualCount = ideaService.getFavoriteIdeasCount();

        // Assert
        assertEquals(expectedCount, actualCount);
        verify(ideaRepository, times(1)).countFavoriteIdeasByUserId(testUser.getId());
    }
}
