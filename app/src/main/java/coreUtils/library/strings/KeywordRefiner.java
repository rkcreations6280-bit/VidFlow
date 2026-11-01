package coreUtils.library.strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility to refine keywords by removing stop words and cleaning up strings.
 */
public class KeywordRefiner {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "how", "to", "the", "a", "an", "and", "or", "but", "if", "then", "else", "when", 
            "where", "why", "who", "which", "this", "that", "these", "those", "is", "are", 
            "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "video", "official", "lyrics", "full", "hd", "4k", "music", "song", "movie", "trailer"
    ));

    /**
     * Refines a string into a list of meaningful keywords.
     */
    public static List<String> refine(String input) {
        if (input == null || input.isEmpty()) return new ArrayList<>();

        String cleanInput = input.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", "") // Remove special characters
                .replaceAll("\\s+", " ")           // Normalize whitespace
                .trim();

        String[] parts = cleanInput.split(" ");
        List<String> keywords = new ArrayList<>();

        for (String part : parts) {
            if (part.length() > 2 && !STOP_WORDS.contains(part)) {
                keywords.add(part);
            }
        }

        return keywords;
    }

    /**
     * Extracts the top N recurring keywords from a list of strings.
     */
    public static List<String> getTopKeywords(List<String> inputs, int limit) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String input : inputs) {
            for (String keyword : refine(input)) {
                counts.put(keyword, counts.getOrDefault(keyword, 0) + 1);
            }
        }

        List<java.util.Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            result.add(list.get(i).getKey());
        }
        return result;
    }
}
