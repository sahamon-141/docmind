package com.example.docmind.dto;

import com.example.docmind.entity.Role;
import com.example.docmind.entity.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String email,
        Role role,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
