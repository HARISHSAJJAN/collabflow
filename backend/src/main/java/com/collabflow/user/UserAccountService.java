package com.collabflow.user;

import com.collabflow.cache.RedisCacheService;
import com.collabflow.common.exception.ConflictException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.user.internal.User;
import com.collabflow.user.internal.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API. Every other module that needs to create, authenticate against, or
 * display a user goes through here rather than touching {@code user.internal} directly.
 *
 * <p>{@link PasswordEncoder} is injected directly here (not routed through the auth module)
 * for changing a password: unlike login/registration, which are inherently auth-module
 * concerns (issuing tokens, deciding whether a request is authenticated), "change my
 * password" is a user-profile operation start to finish - it only happens to need password
 * hashing along the way. {@code PasswordEncoder} is infrastructure defined in the OPEN
 * {@code config} module, so any module is free to use it directly.</p>
 *
 * <p><b>Why {@link #findSummaryById} is cached</b> (see docs/redis.md): {@code UserSummary}
 * (id/email/name/avatar - never the password hash) is looked up repeatedly to render display
 * names - once per member when listing a team/project roster, once per comment author, etc.
 * It changes rarely (only on a profile update) and is read far more often than it's written,
 * making it a textbook cache-aside candidate, unlike {@link #findCredentialsByEmail}, which is
 * never cached: caching credentials would mean a deactivated account or a just-changed
 * password could still authenticate against a stale cached hash until the entry expired -
 * unacceptable for anything security-decision-relevant, however short the TTL.</p>
 */
@Service
public class UserAccountService {

    private static final Duration SUMMARY_CACHE_TTL = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisCacheService cacheService;

    public UserAccountService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            RedisCacheService cacheService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
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
        String key = summaryCacheKey(id);
        Optional<UserSummary> cached = cacheService.get(key, new TypeReference<UserSummary>() {});
        if (cached.isPresent()) {
            return cached;
        }
        Optional<UserSummary> summary = userRepository.findById(id)
                .map(u -> new UserSummary(u.getId(), u.getEmail(), u.getFullName(), u.getAvatarUrl()));
        summary.ifPresent(s -> cacheService.put(key, s, SUMMARY_CACHE_TTL));
        return summary;
    }

    private static String summaryCacheKey(UUID userId) {
        return "user:summary:" + userId;
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

    @Transactional(readOnly = true)
    public UserProfile requireProfileById(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
        return toProfile(user);
    }

    @Transactional
    public UserProfile updateProfile(UUID id, String fullName, String avatarUrl) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
        user.setFullName(fullName.trim());
        user.setAvatarUrl(avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl.trim());
        UserProfile profile = toProfile(userRepository.save(user));
        // After-commit, not immediate: see RedisCacheService#evictAfterCommit's Javadoc for
        // the concurrent-read-repopulates-stale-value race this avoids.
        cacheService.evictAfterCommit(summaryCacheKey(id));
        return profile;
    }

    /** @throws BadCredentialsException if {@code currentRawPassword} does not match the stored hash */
    @Transactional
    public void changePassword(UUID id, String currentRawPassword, String newRawPassword) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
        if (!passwordEncoder.matches(currentRawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        eventPublisher.publishEvent(new PasswordChangedEvent(id, Instant.now()));
    }

    private static UserProfile toProfile(User u) {
        return new UserProfile(u.getId(), u.getEmail(), u.getFullName(), u.getAvatarUrl(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
