package com.collabflow.user;

import com.collabflow.common.exception.ConflictException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.user.internal.User;
import com.collabflow.user.internal.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API. Every other module that needs to create, authenticate against, or
 * display a user goes through here rather than touching {@code user.internal} directly.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;

    public UserAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new user account. The caller (auth module) is responsible for having already
     * hashed the password - this method stores whatever hash it is given and has no opinion
     * about the hashing algorithm.
     *
     * @throws ConflictException if the email is already registered
     */
    @Transactional
    public UUID register(String email, String passwordHash, String fullName) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(normalizedEmail, passwordHash, fullName.trim());
        return userRepository.save(user).getId();
    }

    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .map(u -> new UserCredentials(u.getId(), u.getEmail(), u.getPasswordHash(), u.isActive()));
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummaryById(UUID id) {
        return userRepository.findById(id)
                .map(u -> new UserSummary(u.getId(), u.getEmail(), u.getFullName(), u.getAvatarUrl()));
    }

    public UserSummary requireSummaryById(UUID id) {
        return findSummaryById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Transactional
    public void touchLastLogin(UUID id) {
        userRepository.findById(id).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            userRepository.save(u);
        });
    }
}
