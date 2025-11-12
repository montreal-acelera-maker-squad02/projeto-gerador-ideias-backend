package projeto_gerador_ideias_backend.service;

import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
public class IdeaSummaryService {

    public String summarizeIdeaSimple(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        String trimmed = content.trim();
        String[] words = trimmed.split("\\s+", 11);
        
        if (words.length <= 10) {
            return trimmed;
        }
        
        return summarizeIdeaSimpleFallback(trimmed, words);
    }
    
    private String summarizeIdeaSimpleFallback(String trimmed, String[] words) {
        int maxWords = 5;
        
        String result = findSummaryByPunctuation(words, maxWords, ",", ";");
        if (result != null) {
            return result;
        }
        
        result = findSummaryByPunctuation(words, maxWords, ".", "!", "?");
        if (result != null) {
            return result;
        }
        
        int targetWords = Math.min(maxWords, words.length);
        String[] selectedWords = Arrays.copyOf(words, targetWords);
        selectedWords = removeIncompleteWords(selectedWords, maxWords);
        
        if (selectedWords.length > 0) {
            String summary = joinWords(selectedWords);
            return removeTrailingPunctuation(summary);
        }
        
        return buildFallbackSummary(words, maxWords, trimmed);
    }

    private String findSummaryByPunctuation(String[] words, int maxWords, String... punctuation) {
        int searchLimit = Math.min(words.length, maxWords);
        for (int i = 0; i < searchLimit; i++) {
            if (endsWithAnyPunctuation(words[i], punctuation)) {
                return buildSummaryUpToIndex(words, i, maxWords, punctuation.length > 0 && punctuation[0].equals(","));
            }
        }
        return null;
    }

    private boolean endsWithAnyPunctuation(String word, String... punctuation) {
        if (word == null || word.length() == 0) {
            return false;
        }
        for (String p : punctuation) {
            if (word.endsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private String buildSummaryUpToIndex(String[] words, int endIndex, int maxWords, boolean cleanCommas) {
        StringBuilder summary = new StringBuilder();
        for (int j = 0; j <= endIndex; j++) {
            if (j > 0) {
                summary.append(" ");
            }
            String word = cleanCommas ? removeTrailingCommasAndSemicolons(words[j]) : words[j];
            summary.append(word);
        }
        String result = summary.toString().trim();
        return limitResultWords(result, maxWords);
    }

    private String limitResultWords(String result, int maxWords) {
        String[] resultWords = result.split("\\s+");
        if (resultWords.length > maxWords) {
            return joinWords(Arrays.copyOf(resultWords, maxWords));
        }
        return result;
    }

    private String removeTrailingPunctuation(String text) {
        String[] trailingPunctuation = {":", ";", ",", "-", "—", "–"};
        String result = text.trim();
        while (result.length() > 0 && endsWithAnyPunctuation(result, trailingPunctuation)) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private String removeTrailingCommasAndSemicolons(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        while (result.length() > 0) {
            char lastChar = result.charAt(result.length() - 1);
            if (lastChar == ',' || lastChar == ';') {
                result = result.substring(0, result.length() - 1);
            } else {
                break;
            }
        }
        return result;
    }

    private String[] removeIncompleteWords(String[] words, int maxWords) {
        String[] incompleteWords = {"mais", "menos", "maior", "menor", "melhor", "pior",
                                    "com", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
                                    "a", "o", "os", "as", "ao", "à", "aos", "às", "para", "por", "que"};
        
        String[] result = words;
        while (result.length > 1 && result.length <= maxWords) {
            String lastWord = removeTrailingPunctuationFromString(result[result.length - 1].toLowerCase());
            if (!isIncompleteWord(lastWord, incompleteWords)) {
                break;
            }
            result = Arrays.copyOf(result, result.length - 1);
        }
        return result;
    }

    private String removeTrailingPunctuationFromString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        while (result.length() > 0) {
            char lastChar = result.charAt(result.length() - 1);
            if (lastChar == ':' || lastChar == ';' || lastChar == ',' || lastChar == '.' || lastChar == '!' || lastChar == '?') {
                result = result.substring(0, result.length() - 1);
            } else {
                break;
            }
        }
        return result;
    }

    private boolean isIncompleteWord(String word, String[] incompleteWords) {
        for (String incomplete : incompleteWords) {
            if (word.equals(incomplete)) {
                return true;
            }
        }
        return false;
    }

    private String joinWords(String[] words) {
        if (words.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            result.append(words[i]);
        }
        return result.toString().trim();
    }

    private String buildFallbackSummary(String[] words, int maxWords, String trimmed) {
        if (words.length >= 3) {
            int wordsToTake = Math.min(3, maxWords);
            return joinWords(Arrays.copyOf(words, wordsToTake));
        }
        return trimmed;
    }
}


