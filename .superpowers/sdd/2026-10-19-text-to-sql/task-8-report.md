# Task 8 report
Implemented examples.yaml (26 examples, VI+EN, all dw.-qualified, English aliases), FewShotExample, TextNormalizer (normalize, tokens with stopwords + bigrams), FewShotSelector (cached YAML, Jaccard, stable sort, min(k,size)).
Headline eval question "doanh thu thang 9 theo khu vuc" deliberately NOT included.
Tests: FewShotSelectorTest (6, unit), FewShotExamplesValidTest (guard allowed + executes + non-empty rows for every example). Full `./mvnw test`: 233 run, 0 failures, BUILD SUCCESS.
TDD note: implementation and tests were written together; no separate RED run recorded.

## Fix round 1
examples.yaml only: tracks grouped by track_key+track_name+artist_name; employee examples grouped by employee_key+full_name; deterministic tiebreakers added to all top-N/LIMIT examples (artists, tracks, genre, employee, customers, cities); "Khách hàng mua nhiều nhất" reworded to "Khách hàng chi tiêu nhiều nhất". Other groupings (artist_name, genre_name, country, city, region) left as-is.
Tests: `./mvnw test -Dtest='FewShot*Test'` -> 7 run, 0 failures, BUILD SUCCESS.
