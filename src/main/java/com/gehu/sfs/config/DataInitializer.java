package com.gehu.sfs.config;

import com.gehu.sfs.entity.Enums.Role;
import com.gehu.sfs.entity.Folder;
import com.gehu.sfs.entity.User;
import com.gehu.sfs.repository.FolderRepository;
import com.gehu.sfs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the default administrator on first start (password hashed with BCrypt). */
@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users; private final FolderRepository folders; private final PasswordEncoder enc;
    @Value("${sfs.admin.username}") String adminUser;
    @Value("${sfs.admin.password}") String adminPass;
    @Value("${sfs.admin.email}") String adminEmail;
    public DataInitializer(UserRepository u, FolderRepository f, PasswordEncoder e) { users = u; folders = f; enc = e; }

    public void run(String... args) {
        if (users.existsByUsername(adminUser)) return;
        User u = new User();
        u.setUsername(adminUser); u.setEmail(adminEmail); u.setPasswordHash(enc.encode(adminPass)); u.setRole(Role.ADMIN);
        users.save(u);
        Folder root = new Folder();
        root.setName("My Files"); root.setOwnerId(u.getId());
        folders.save(root);
        System.out.println(">>> Default admin created: " + adminUser + " (change the password in application.properties)");
    }
}
