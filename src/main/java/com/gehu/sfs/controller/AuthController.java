package com.gehu.sfs.controller;

import com.gehu.sfs.dto.Requests.Login;
import com.gehu.sfs.dto.Requests.Register;
import com.gehu.sfs.dto.Views.UserView;
import com.gehu.sfs.entity.Folder;
import com.gehu.sfs.entity.User;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.FolderRepository;
import com.gehu.sfs.repository.UserRepository;
import com.gehu.sfs.security.CurrentUser;
import com.gehu.sfs.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthenticationManager authManager; private final UserRepository users; private final FolderRepository folders;
    private final PasswordEncoder encoder; private final AuditService audit;
    public AuthController(AuthenticationManager am, UserRepository u, FolderRepository f, PasswordEncoder e, AuditService a) {
        authManager = am; users = u; folders = f; encoder = e; audit = a;
    }

    private UserView view(User u) { return new UserView(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name(), u.getCreatedAt()); }

    @PostMapping("/auth/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody Register req) {
        if (users.existsByUsername(req.username())) throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
        if (users.existsByEmail(req.email())) throw ApiException.conflict("EMAIL_TAKEN", "Email is already registered");
        User u = new User();
        u.setUsername(req.username()); u.setEmail(req.email()); u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);
        Folder root = new Folder(); root.setName("My Files"); root.setOwnerId(u.getId());
        folders.save(root);
        audit.log(u.getId(), "USER_REGISTERED", "New user " + u.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(u));
    }

    @PostMapping("/auth/login")
    public UserView login(@Valid @RequestBody Login req, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException e) {
            audit.log(null, "LOGIN_FAILED", "Failed login for username '" + req.username().replaceAll("[^A-Za-z0-9_.-]", "?") + "'");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password");
        }
        request.getSession(true);
        request.changeSessionId();                       // session-fixation protection
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        new HttpSessionSecurityContextRepository().saveContext(ctx, request, response);
        User u = users.findByUsername(req.username()).orElseThrow();
        audit.log(u.getId(), "LOGIN", u.getUsername() + " logged in");
        return view(u);
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        var s = request.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("status", "logged out");
    }

    @GetMapping("/users/me")
    public UserView me() {
        return view(users.findById(CurrentUser.id()).orElseThrow());
    }
}
