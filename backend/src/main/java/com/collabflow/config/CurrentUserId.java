package com.collabflow.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's id directly as a {@code UUID} controller method parameter,
 * e.g. {@code listMySessions(@CurrentUserId UUID userId)}. Resolved by
 * {@link CurrentUserIdArgumentResolver} from the {@code Authentication} that
 * {@link JwtAuthenticationFilter} placed in the security context - controllers never touch
 * {@code SecurityContextHolder} or parse the principal themselves.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
