# Task 3 report
Implemented semantic-layer.yaml (6 dw tables, 3 metrics, 5 rules; sample values from real data), SemanticLayer records, SemanticLayerLoader (YAMLMapper, cached), SchemaPromptRenderer (dw.table(col type -- desc; đồng nghĩa; ví dụ), Metrics, Rules).
Tests: SemanticLayerConsistencyTest (3) + SchemaPromptRendererTest (3) pass; full ./mvnw test: 20 tests, 0 failures.
TDD note: tests and implementation were written in the same step; the RED run was not captured (deviation).
Concern: region synonyms are written as "Bắc Mỹ = North America" strings in the synonyms list.

## Fix round 1
Changes: region synonyms now only [khu vực, vùng, region]; added rule line with explicit Vietnamese->English value mapping ('North America', 'South America', 'Europe', 'Asia-Pacific'); SchemaPromptRendererTest gained 3 tests (column-line shape, Metrics before Rules headers, Bắc Mỹ and 'North America' on same line + no "=" mapping).
RED: ./mvnw -q test -Dtest=SchemaPromptRendererTest -> Tests run: 6, Failures: 1 (regionMappingIsExplicitAndNotInSynonyms). Shape/order tests already passed (renderer format was correct). (An earlier RED had compile errors from my escaping; fixed the test before counting.)
GREEN: ./mvnw -q test -Dtest='SchemaPromptRendererTest,SemanticLayerConsistencyTest' -> Renderer 6/6, Consistency 3/3, 0 failures.
