package com.fsoft.training.warehouse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class SemanticLayerLoader {

    private static final String RESOURCE = "semantic/semantic-layer.yaml";

    private volatile SemanticLayer cached;

    public SemanticLayer load() {
        SemanticLayer layer = cached;
        if (layer == null) {
            synchronized (this) {
                layer = cached;
                if (layer == null) {
                    layer = read();
                    cached = layer;
                }
            }
        }
        return layer;
    }

    private SemanticLayer read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return YAMLMapper.builder().build().readValue(in, SemanticLayer.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
