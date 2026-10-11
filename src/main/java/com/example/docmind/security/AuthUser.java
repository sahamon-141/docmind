package com.example.docmind.security;

import com.example.docmind.entity.Role;
import com.example.docmind.entity.User;

// Authenticated principal stored in the SecurityContext. Built from the database, never from client input.
public record AuthUser(Long id, String username, Role role) {
    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }
}
