package projeto_gerador_ideias_backend.service;

import org.springframework.stereotype.Service;

@Service
public class PromptSanitizer {

    public String sanitizeForPrompt(String content) {
        if (content == null) {
            return "";
        }
        
        return content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", " ")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    public String escapeForFormat(String content) {
        if (content == null) {
            return "";
        }
        
        String sanitized = sanitizeForPrompt(content);
        
        return sanitized
                .replace("%", "%%")
                .replace("{", "{{")
                .replace("}", "}}");
    }
}

