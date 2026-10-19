package com.fsoft.training.auth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint proving a valid token reaches a controller and CurrentUser resolves the id. */
@RestController
class TestMeController {

    @GetMapping("/api/test/me")
    Map<String, Object> me(Authentication authentication) {
        return Map.of("id", CurrentUser.id(authentication),
                "username", ((Jwt) authentication.getPrincipal()).getClaimAsString("username"));
    }
}
