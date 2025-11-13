package projeto_gerador_ideias_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.ThemeRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThemeServiceTest {

    @Mock
    private ThemeRepository themeRepository;

    @InjectMocks
    private ThemeService themeService;

    private Theme theme;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        theme = new Theme("Tecnologia");
        theme.setId(1L);
    }

    @Test
    void deveListarTodosOsTemas() {
        when(themeRepository.findAll()).thenReturn(Arrays.asList(theme, new Theme("Educação")));

        List<Theme> themes = themeService.getAll();

        assertEquals(2, themes.size());
        verify(themeRepository, times(1)).findAll();
    }

    @Test
    void deveBuscarTemaPorIdExistente() {
        when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));

        Theme resultado = themeService.findByID(1L);

        assertNotNull(resultado);
        assertEquals("Tecnologia", resultado.getName());
        verify(themeRepository, times(1)).findById(1L);
    }

    @Test
    void deveLancarExcecaoAoBuscarTemaPorIdInexistente() {
        when(themeRepository.findById(99L)).thenReturn(Optional.empty());

        Long invalidId = 99L;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> themeService.findByID(invalidId));

        assertEquals("Tema não encontrado.", ex.getMessage());
    }

    @Test
    void deveBuscarTemaPorNomeExistente() {
        when(themeRepository.findByNameIgnoreCase("tecnologia")).thenReturn(Optional.of(theme));

        Theme resultado = themeService.findByName("tecnologia");

        assertNotNull(resultado);
        assertEquals("Tecnologia", resultado.getName());
        verify(themeRepository).findByNameIgnoreCase("tecnologia");
    }

    @Test
    void deveLancarExcecaoAoBuscarTemaPorNomeInexistente() {
        when(themeRepository.findByNameIgnoreCase("finanças")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> themeService.findByName("finanças"));

        assertEquals("Tema não encontrado: finanças", ex.getMessage());
    }

    @Test
    void deveCriarNovoTema() {
        when(themeRepository.findByNameIgnoreCase("Tecnologia")).thenReturn(Optional.empty());
        when(themeRepository.save(any(Theme.class))).thenReturn(theme);

        Theme criado = themeService.createTheme(theme);

        assertNotNull(criado);
        assertEquals("Tecnologia", criado.getName());
        verify(themeRepository).save(theme);
    }

    @Test
    void deveLancarExcecaoAoCriarTemaDuplicado() {
        when(themeRepository.findByNameIgnoreCase("Tecnologia")).thenReturn(Optional.of(theme));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> themeService.createTheme(theme));

        assertEquals("Tema já existe.", ex.getMessage());
        verify(themeRepository, never()).save(any());
    }

    @Test
    void deveAtualizarTemaExistente() {
        Theme novoTema = new Theme("Ciência");
        when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));
        when(themeRepository.save(any(Theme.class))).thenAnswer(inv -> inv.getArgument(0));

        Theme atualizado = themeService.updateTheme(1L, novoTema);

        assertEquals("Ciência", atualizado.getName());
        verify(themeRepository).save(theme);
    }

    @Test
    void deveLancarExcecaoAoAtualizarTemaInexistente() {
        when(themeRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> themeService.updateTheme(1L, new Theme("Outro")));

        assertEquals("Tema não encontrado.", ex.getMessage());
    }

    @Test
    void deveDeletarTemaExistente() {
        when(themeRepository.findById(1L)).thenReturn(Optional.of(theme));
        doNothing().when(themeRepository).delete(theme);

        themeService.deleteTheme(1L);

        verify(themeRepository).delete(theme);
    }

    @Test
    void deveLancarExcecaoAoDeletarTemaInexistente() {
        when(themeRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> themeService.deleteTheme(1L));

        assertEquals("Tema não encontrado.", ex.getMessage());
        verify(themeRepository, never()).delete(any());
    }
}
