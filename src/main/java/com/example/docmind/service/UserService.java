package com.example.docmind.service;

import com.example.docmind.dto.ChangePasswordRequest;
import com.example.docmind.dto.RegisterUserRequest;
import com.example.docmind.dto.UpdateProfileRequest;
import com.example.docmind.dto.UserDto;
import com.example.docmind.entity.RefreshToken;
import com.example.docmind.entity.Role;
import com.example.docmind.entity.User;
import com.example.docmind.exception.ApiException;
import com.example.docmind.exception.ConflictException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public User registerUser(RegisterUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("USERNAME_TAKEN", "Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        //encode the password and then store it into the database
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        // the case-insensitive unique indexes still catch a concurrent duplicate (mapped to 409)
        return userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        user.setEmail(email);
        return UserDto.from(userRepository.saveAndFlush(user));
    }

    // Changing the password signs out every session by revoking all refresh tokens.
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(userId, RefreshToken.REASON_PASSWORD_CHANGED);
    }
}
