package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projeto_gerador_ideias_backend.model.Theme;
import projeto_gerador_ideias_backend.repository.ThemeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeRepository themeRepository;

    public List<Theme> getAll() {
        return themeRepository.findAll();
    }

    public Theme findByID(Long id) {
        return themeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tema não encontrado."));
    }

    public Theme findByName(String nome) {
        return themeRepository.findByNameIgnoreCase(nome)
                .orElseThrow(() -> new IllegalArgumentException("Tema não encontrado: " + nome));
    }

    @Transactional
    public Theme createTheme(Theme theme) {
        if (themeRepository.findByNameIgnoreCase(theme.getName()).isPresent()) {
            throw new IllegalArgumentException("Tema já existe.");
        }
        return themeRepository.save(theme);
    }

    @Transactional
    public Theme updateTheme(Long id, Theme novoTema) {
        Theme temaExistente = findByID(id);
        temaExistente.setName(novoTema.getName());
        return themeRepository.save(temaExistente);
    }

    @Transactional
    public void deleteTheme(Long id) {
        Theme tema = findByID(id);
        themeRepository.delete(tema);
    }
}
