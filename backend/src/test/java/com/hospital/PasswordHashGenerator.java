package com.hospital;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    @Test
    void generateHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("admin123");
        System.out.println("=== BCrypt Hash for 'admin123' ===");
        System.out.println(hash);
        System.out.println("===================================");
        System.out.println("Verify: " + encoder.matches("admin123", hash));
    }
}
