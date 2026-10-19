package com.fsoft.training.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fsoft.training.support.PostgresIntegrationTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SemanticLayerConsistencyTest extends PostgresIntegrationTest {

    @Autowired
    SemanticLayerLoader loader;

    private Set<String> yamlColumns() {
        Set<String> s = new HashSet<>();
        for (SemanticLayer.Table t : loader.load().tables()) {
            for (SemanticLayer.Column c : t.columns()) {
                s.add(t.name() + "." + c.name());
            }
        }
        return s;
    }

    private Set<String> dbColumns() {
        List<String> rows = superuserJdbc().queryForList(
                "select table_name || '.' || column_name from information_schema.columns where table_schema = 'dw'",
                String.class);
        return new HashSet<>(rows);
    }

    @Test
    void everyYamlColumnExistsInDw() {
        Set<String> missing = yamlColumns();
        missing.removeAll(dbColumns());
        assertThat(missing).isEmpty();
    }

    @Test
    void everyDwColumnIsDescribedInYaml() {
        Set<String> undocumented = dbColumns();
        undocumented.removeAll(yamlColumns());
        assertThat(undocumented).isEmpty();
    }

    @Test
    void everyColumnHasDescription() {
        loader.load().tables().forEach(t -> t.columns()
                .forEach(c -> assertThat(c.description()).as(t.name() + "." + c.name()).isNotBlank()));
    }
}
