# Task 2 report
Implemented db/init/02_build_dw.sql (dw schema: country_region, dim_date, dim_customer, dim_employee, dim_track, fact_sales; PK/FK, COMMENTs, GRANT USAGE + SELECT to analyst_ro; re-runnable via DROP SCHEMA CASCADE). Region list derived from chinook.customer countries.
Tests: WarehouseBuildTest (5), AnalystRolePermissionTest (6; includes pg_sleep(11) timeout).
RED: `./mvnw -q test -Dtest='WarehouseBuildTest,AnalystRolePermissionTest'` -> 1+5 errors (BadSqlGrammar, script not found).
GREEN: `./mvnw -q test` -> exit 0; surefire: 6/3/5 tests, 0 errors, 0 failures.
Notes: re-run test executes the file via ScriptUtils on a superuser connection and checks grants re-applied. No ALTER DEFAULT PRIVILEGES needed.
