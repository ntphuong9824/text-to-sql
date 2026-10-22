package com.fsoft.training.nl2sql;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TextNormalizer {

    private static final Set<String> STOPWORDS = Set.of(
            "la", "cua", "the", "of", "cho", "theo", "va", "and", "in", "by", "a", "an", "to",
            "trong", "voi", "cac", "nhung", "mot", "is", "are", "what", "me", "toi");

    private TextNormalizer() {}

    /** Lowercase, strip Vietnamese diacritics (d with stroke becomes d), drop punctuation, collapse spaces. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return s.replaceAll("\\s+", " ").strip();
    }

    /** Unigrams without stopwords plus bigrams of adjacent remaining words (prefixed with "_"). */
    public static Set<String> tokens(String text) {
        String normalized = normalize(text);
        Set<String> result = new LinkedHashSet<>();
        if (normalized.isEmpty()) {
            return result;
        }
        List<String> words = Arrays.stream(normalized.split(" "))
                .filter(w -> !STOPWORDS.contains(w))
                .toList();
        result.addAll(words);
        for (int i = 0; i + 1 < words.size(); i++) {
            result.add("_" + words.get(i) + " " + words.get(i + 1));
        }
        return result;
    }
}
