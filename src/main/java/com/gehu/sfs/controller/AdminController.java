package com.gehu.sfs.controller;

import com.gehu.sfs.dto.Requests.RoleReq;
import com.gehu.sfs.dto.Views.UserView;
import com.gehu.sfs.entity.User;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.UserRepository;
import com.gehu.sfs.security.CurrentUser;
import com.gehu.sfs.service.AnalyticsService;
import com.gehu.sfs.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final UserRepository users; private final AuditService audit; private final AnalyticsService analytics;
    public AdminController(UserRepository u, AuditService a, AnalyticsService an) { users = u; audit = a; analytics = an; }

    @GetMapping("/admin/users")
    public List<UserView> listUsers() {
        return users.findAll().stream().map(u -> new UserView(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name(), u.getCreatedAt())).toList();
    }

    @PutMapping("/admin/users/{id}/role")
    public Map<String, String> setRole(@PathVariable Integer id, @Valid @RequestBody RoleReq req) {
        if (id.equals(CurrentUser.id())) throw ApiException.bad("You cannot change your own role");
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setRole(req.role()); users.save(u);
        audit.log(CurrentUser.id(), "ROLE_CHANGED", "user_id=" + id + " is now " + req.role());
        return Map.of("status", "ok");
    }

    @GetMapping("/admin/storage")
    public List<Map<String, Object>> storageByUser() { return analytics.storageByUser(); }

    @GetMapping("/audit-logs")
    public Map<String, Object> audit(@RequestParam(required = false) Integer userId, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size) {
        return analytics.auditLogs(userId, page, size);
    }
}
