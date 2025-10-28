package projeto_gerador_ideias_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import projeto_gerador_ideias_backend.dto.IdeaRequest;
import projeto_gerador_ideias_backend.dto.IdeaResponse;
import projeto_gerador_ideias_backend.repository.IdeaRepository;

@Service
@RequiredArgsConstructor
public class IdeaService {

    private final IdeaRepository ideaRepository;

    public IdeaResponse generateIdea(IdeaRequest request) {
        IdeaResponse mockResponse = new IdeaResponse();
        mockResponse.setContent("Uma ideia incrível sobre " + request.getTheme() + " gerada pela IA!");
        return mockResponse;
    }
}