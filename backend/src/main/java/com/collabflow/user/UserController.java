package com.collabflow.user;

import com.collabflow.config.CurrentUserId;
import com.collabflow.user.dto.ChangePasswordRequest;
import com.collabflow.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile self-service: every endpoint here acts on the authenticated caller's own account
 * (resolved via {@link CurrentUserId}), never on an arbitrary user id from the URL - there is
 * deliberately no {@code PATCH /users/{id}} for editing someone else.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userAccountService.requireProfileById(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfile> updateMyProfile(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userAccountService.updateProfile(userId, request.fullName(), request.avatarUrl()));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@CurrentUserId UUID userId, @Valid @RequestBody ChangePasswordRequest request) {
        userAccountService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
