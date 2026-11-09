package com.gehu.sfs.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}
    public static SfsUserDetails get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (SfsUserDetails) a.getPrincipal();
    }
    public static Integer id() { return get().getId(); }
    public static boolean isAdmin() { return "ADMIN".equals(get().getRole()); }
}
