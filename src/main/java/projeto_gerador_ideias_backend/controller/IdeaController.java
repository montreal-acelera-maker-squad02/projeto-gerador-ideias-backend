package projeto_gerador_ideias_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.service.IdeaService;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;

    @PostMapping("/generate")
    public ResponseEntity<IdeaResponse> generateIdea(@RequestBody IdeaRequest request) {
        IdeaResponse response = ideaService.generateIdea(request);
        return ResponseEntity.ok(response);
    }
}