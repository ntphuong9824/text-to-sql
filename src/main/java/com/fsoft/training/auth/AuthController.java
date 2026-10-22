package com.fsoft.training.auth;

import com.fsoft.training.auth.AuthDtos.AuthResponse;
import com.fsoft.training.auth.AuthDtos.ErrorResponse;
import com.fsoft.training.auth.AuthDtos.LoginRequest;
import com.fsoft.training.auth.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BAD_CREDENTIALS = "Sai tên đăng nhập hoặc mật khẩu";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Verified against when the username is unknown, so both failure paths cost one BCrypt check. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        long id = users.create(request.username(), passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwtService.issue(id, request.username()), request.username()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var user = users.findByUsername(request.username());
        // Over-long passwords can never match (registration rejects them) and BCrypt would throw on them.
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > AuthDtos.MAX_BCRYPT_BYTES;
        boolean matches = !tooLong && passwordEncoder.matches(request.password(),
                user.map(UserRepository.UserRow::passwordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(BAD_CREDENTIALS));
        }
        var row = user.get();
        return ResponseEntity.ok(new AuthResponse(jwtService.issue(row.id(), row.username()), row.username()));
    }
}
