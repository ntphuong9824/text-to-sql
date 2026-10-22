package com.fsoft.training.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Authentication authentication) {
        return Long.parseLong(((Jwt) authentication.getPrincipal()).getSubject());
    }
}
