package com.fsoft.training.nl2sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class FewShotSelector {

    private static final String RESOURCE = "fewshot/examples.yaml";

    record Document(List<FewShotExample> examples) {}

    private record Indexed(FewShotExample example, Set<String> tokens) {}

    private volatile List<Indexed> cached;

    public List<FewShotExample> all() {
        return index().stream().map(Indexed::example).toList();
    }

    /** Exactly min(k, all().size()) examples, best Jaccard score first, file order on ties. */
    public List<FewShotExample> select(String question, int k) {
        Set<String> query = TextNormalizer.tokens(question);
        List<Indexed> indexed = index();
        double[] scores = new double[indexed.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < indexed.size(); i++) {
            scores[i] = jaccard(query, indexed.get(i).tokens());
            order.add(i);
        }
        // List.sort is stable, so equal scores keep file order.
        order.sort((a, b) -> Double.compare(scores[b], scores[a]));
        return order.stream().limit(Math.max(0, k)).map(i -> indexed.get(i).example()).toList();
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        int union = a.size() + b.size() - inter.size();
        return (double) inter.size() / union;
    }

    private List<Indexed> index() {
        List<Indexed> local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = read();
                    cached = local;
                }
            }
        }
        return local;
    }

    private List<Indexed> read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Document doc = YAMLMapper.builder().build().readValue(in, Document.class);
            return doc.examples().stream()
                    .map(e -> new Indexed(e, TextNormalizer.tokens(e.question())))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
