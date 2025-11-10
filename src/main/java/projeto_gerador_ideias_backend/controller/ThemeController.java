package projeto_gerador_ideias_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.service.ThemeService;

import java.util.List;

@RestController
@RequestMapping("/api/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    @Operation(summary = "Listar todos os temas")
    @GetMapping
    public ResponseEntity<List<Theme>> listarTemas() {
        return ResponseEntity.ok(themeService.getAll());
    }

    @Operation(summary = "Buscar tema por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Theme> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(themeService.findByID(id));
    }

    @Operation(summary = "Criar novo tema")
    @PostMapping
    public ResponseEntity<Theme> criarTema(@RequestBody Theme theme) {
        return ResponseEntity.ok(themeService.createTheme(theme));
    }

    @Operation(summary = "Atualizar tema")
    @PutMapping("/{id}")
    public ResponseEntity<Theme> atualizarTema(@PathVariable Long id, @RequestBody Theme theme) {
        return ResponseEntity.ok(themeService.updateTheme(id, theme));
    }

    @Operation(summary = "Deletar tema")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTema(@PathVariable Long id) {
        themeService.deleteTheme(id);
        return ResponseEntity.noContent().build();
    }
}
