package com.fsoft.training.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public final class AuthDtos {

    static final int MAX_BCRYPT_BYTES = 72;

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Tên đăng nhập không được để trống")
            @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
            @Pattern(regexp = "[a-zA-Z0-9_.]+", message = "Tên đăng nhập chỉ gồm chữ, số, dấu gạch dưới và dấu chấm")
            String username,
            @NotNull(message = "Mật khẩu không được để trống")
            @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
            String password) {

        /** BCrypt only supports passwords up to 72 bytes and Spring rejects longer ones. */
        @AssertTrue(message = "Mật khẩu quá dài (tối đa 72 byte)")
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Tên đăng nhập không được để trống") String username,
            @NotBlank(message = "Mật khẩu không được để trống") String password) {
    }

    public record AuthResponse(String token, String username) {
    }

    public record ErrorResponse(String message) {
    }
}
