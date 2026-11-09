package com.gehu.sfs.security;

import com.gehu.sfs.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SfsUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public SfsUserDetailsService(UserRepository users) { this.users = users; }
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
            .map(u -> new SfsUserDetails(u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole().name()))
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
