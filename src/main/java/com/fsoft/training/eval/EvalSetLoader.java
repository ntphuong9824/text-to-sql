package com.fsoft.training.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads the eval set YAML (snake_case keys, e.g. {@code gold_sql}) from the filesystem. */
public final class EvalSetLoader {

    private EvalSetLoader() {}

    public static EvalSet load(Path path) {
        YAMLMapper mapper = YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        try (InputStream in = Files.newInputStream(path)) {
            EvalSet raw = mapper.readValue(in, EvalSet.class);
            return new EvalSet(orEmpty(raw.items()), orEmpty(raw.conversations()), orEmpty(raw.attacks()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read eval set " + path.toAbsolutePath(), e);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
