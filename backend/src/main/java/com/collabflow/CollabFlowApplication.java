package com.collabflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code UserDetailsServiceAutoConfiguration} is excluded on purpose: this app authenticates
 * with its own JWT filter ({@code config.JwtAuthenticationFilter}) and its own credential
 * check ({@code auth.AuthService}, comparing against {@code user.UserAccountService}), never
 * through Spring Security's {@code UserDetailsService}/{@code AuthenticationManager}
 * machinery. Left enabled, this autoconfiguration generates and logs a random default-user
 * password on every startup - functionally harmless here (nothing uses it), but misleading
 * log noise that could make a reader think Spring's default form-login is in play.
 *
 * <p>{@code @EnableScheduling} backs exactly one job so far:
 * {@code notification.internal.DueDateReminderJob} (Phase 11) - a due-date-approaching
 * notification is the one notification type in this codebase that isn't triggered by any
 * event, since nothing "happens" for it; it has to be found by a periodic sweep instead.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class CollabFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollabFlowApplication.class, args);
    }
}
