package projeto_gerador_ideias_backend.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import projeto_gerador_ideias_backend.idea.dto.IdeaCreateRequest;
import projeto_gerador_ideias_backend.idea.dto.IdeaResponse;

import java.util.concurrent.atomic.AtomicLong;

@Profile({"dev", "mock"})
@RestController
@RequestMapping("/api/mock/ideas")
@Slf4j
public class IdeaMockController {

    private final AtomicLong idCounter = new AtomicLong();

    @PostMapping
    public ResponseEntity<IdeaResponse> createIdea(@Valid @RequestBody IdeaCreateRequest request) {
        log.info("Requisição de criação de ideia MOCK recebida: {}", request.getTitle());

        // resposta mockada
        IdeaResponse mockResponse = new IdeaResponse();
        mockResponse.setId(idCounter.incrementAndGet()); // Gera um ID sequencial mock
        mockResponse.setTitle(request.getTitle());
        mockResponse.setDescription(request.getDescription());
        mockResponse.setCategory(request.getCategory());

        return new ResponseEntity<>(mockResponse, HttpStatus.CREATED);
    }
}