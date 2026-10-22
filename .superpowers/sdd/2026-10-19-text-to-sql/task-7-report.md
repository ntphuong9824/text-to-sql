# Task 7 report
Implemented llm package (ChatMessage, LlmClient, LlmException, LlmProperties, Sleeper, LlmConfig, OpenAiCompatibleLlmClient), llm.* in application.yml, @ConfigurationPropertiesScan, FakeLlmClient + FakeLlmConfig (@Primary) imported by PostgresIntegrationTest, FakeLlmClientTest, OpenAiCompatibleLlmClientTest (11 tests).
Added dependency spring-boot-starter-restclient: Boot 4 does not auto-configure RestClient.Builder without it (context failed to load: no RestClient$Builder bean; seen as 37 errors in the full run before the fix).
Timeouts: LlmConfig clones the injected builder and sets SimpleClientHttpRequestFactory (done in config, not in the client, so MockRestServiceServer binding stays intact).
Result: ./mvnw test -> Tests run: 222, Failures: 0, Errors: 0, BUILD SUCCESS (app context starts with empty LLM_API_KEY).
Note: RED phase was not run separately (the first compile attempt failed on missing classes/imports; tests were written alongside the implementation).

## Fix round 1
- LlmConfig now builds the client via static create(); request factory switched to JdkClientHttpRequestFactory (java.net.http.HttpClient, connect timeout) + setReadTimeout(timeout).
- New LlmClientHttpTest (real com.sun.net.httpserver stub, production factory path): 401+WWW-Authenticate, 200, slow server (200ms timeout).
- New LlmContextTest (ApplicationContextRunner, LlmConfig + LlmProperties, empty api-key, real bean present).
- RED on old SimpleClientHttpRequestFactory: the 401 test did NOT reproduce on this JDK (passed). The timeout test did fail: waited ~2s and returned "Lỗi khi gọi dịch vụ AI" instead of "Dịch vụ AI không phản hồi", i.e. the timeout was not effectively applied. GREEN after the switch.
- Full suite: 226 run, 0 failures, BUILD SUCCESS.
