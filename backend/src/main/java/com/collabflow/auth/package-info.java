/**
 * Authentication module: registration, login, logout, JWT access/refresh token issuance
 * and rotation, and password change.
 *
 * <p>This module owns the {@code refresh_tokens} table and the {@code JwtService}. Other
 * modules never verify tokens themselves - authentication is enforced once, centrally, by
 * the servlet filter chain in {@code config}, and downstream code reads the already-verified
 * principal from the security context. This module is a regular (encapsulated) Spring
 * Modulith module: only types in this root package are part of its public API, everything
 * in a subpackage (e.g. internal token repository) is invisible to other modules.</p>
 */
package com.collabflow.auth;
