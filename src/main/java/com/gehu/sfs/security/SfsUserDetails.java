package com.gehu.sfs.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public class SfsUserDetails implements UserDetails {
    private final Integer id; private final String username; private final String passwordHash; private final String role;
    public SfsUserDetails(Integer id, String username, String passwordHash, String role) {
        this.id = id; this.username = username; this.passwordHash = passwordHash; this.role = role;
    }
    public Integer getId() { return id; }
    public String getRole() { return role; }
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    public String getPassword() { return passwordHash; }
    public String getUsername() { return username; }
    public boolean isAccountNonExpired() { return true; }
    public boolean isAccountNonLocked() { return true; }
    public boolean isCredentialsNonExpired() { return true; }
    public boolean isEnabled() { return true; }
}
