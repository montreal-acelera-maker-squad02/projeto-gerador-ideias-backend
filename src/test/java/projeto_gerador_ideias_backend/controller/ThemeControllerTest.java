package projeto_gerador_ideias_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.service.ThemeService;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ThemeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ThemeService themeService;

    @InjectMocks
    private ThemeController themeController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(themeController).build();
    }

    @Test
    void deveListarTodosOsTemas() throws Exception {
        List<Theme> themes = Arrays.asList(new Theme("Tecnologia"), new Theme("Educação"));
        when(themeService.getAll()).thenReturn(themes);

        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Tecnologia")))
                .andExpect(jsonPath("$[1].name", is("Educação")));

        verify(themeService, times(1)).getAll();
    }

    @Test
    void deveBuscarTemaPorId() throws Exception {
        Theme theme = new Theme("Saúde");
        theme.setId(1L);
        when(themeService.findByID(1L)).thenReturn(theme);

        mockMvc.perform(get("/api/themes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Saúde")));

        verify(themeService, times(1)).findByID(1L);
    }

    @Test
    void deveCriarNovoTema() throws Exception {
        Theme novoTheme = new Theme("Esportes");
        novoTheme.setId(10L);

        when(themeService.createTheme(any(Theme.class))).thenReturn(novoTheme);

        mockMvc.perform(post("/api/themes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Esportes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Esportes")));

        verify(themeService, times(1)).createTheme(any(Theme.class));
    }

    @Test
    void deveAtualizarTema() throws Exception {
        Theme atualizado = new Theme("Negócios");
        atualizado.setId(2L);

        when(themeService.updateTheme(eq(2L), any(Theme.class))).thenReturn(atualizado);

        mockMvc.perform(put("/api/themes/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Negócios\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.name", is("Negócios")));

        verify(themeService, times(1)).updateTheme(eq(2L), any(Theme.class));
    }

    @Test
    void deveDeletarTema() throws Exception {
        doNothing().when(themeService).deleteTheme(5L);

        mockMvc.perform(delete("/api/themes/5"))
                .andExpect(status().isNoContent());

        verify(themeService, times(1)).deleteTheme(5L);
    }
}
