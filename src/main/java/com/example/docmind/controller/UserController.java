package com.example.docmind.controller;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.ApiResponse;
import com.example.docmind.dto.UsageLimitsDto;
import com.example.docmind.repository.DocumentMetaDataRepo;
import com.example.docmind.service.RateLimiterService;
import com.example.docmind.dto.ChangePasswordRequest;
import com.example.docmind.dto.UpdateProfileRequest;
import com.example.docmind.dto.UserDto;
import com.example.docmind.security.AuthUser;
import com.example.docmind.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Account", description = "Profile settings of the authenticated user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final DocumentMetaDataRepo documentRepo;
    private final RateLimiterService rateLimiter;
    private final AppProperties appProperties;

    @GetMapping("/limits")
    @Operation(summary = "Document quota, upload size limit and remaining chat/search/upload budget")
    public ResponseEntity<ApiResponse<UsageLimitsDto>> limits(@AuthenticationPrincipal AuthUser user) {
        AppProperties.DocumentProperties docs = appProperties.getDocuments();
        UsageLimitsDto limits = new UsageLimitsDto(
                docs.getMaxDocumentsPerUser(),
                documentRepo.countByOwnerId(user.id()),
                docs.getMaxFileSize().toBytes(),
                rateLimiter.usage(user.id(), RateLimiterService.Action.CHAT),
                rateLimiter.usage(user.id(), RateLimiterService.Action.SEARCH),
                rateLimiter.usage(user.id(), RateLimiterService.Action.UPLOAD));
        return ResponseEntity.ok(ApiResponse.ok(limits, null));
    }

    @PatchMapping
    @Operation(summary = "Update the email address", description = "409 EMAIL_TAKEN if another account uses it.")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(@AuthenticationPrincipal AuthUser user,
                                                              @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(user.id(), request), "Profile updated"));
    }

    @PostMapping("/password")
    @Operation(summary = "Change the password", description = "Revokes every refresh token of the account, signing out all sessions.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal AuthUser user,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed. Please sign in again."));
    }
}
