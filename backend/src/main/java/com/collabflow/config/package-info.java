/**
 * Application-wide configuration: Spring Security filter chain, CORS, OpenAPI/Swagger,
 * web MVC configuration (pagination defaults, argument resolvers) and other
 * {@code @Configuration} classes that apply across module boundaries.
 *
 * <p>Marked OPEN for the same reason as {@code common}: configuration is infrastructure,
 * not domain logic, so every module is allowed to reference it (e.g. a security constant
 * or a shared bean).</p>
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.collabflow.config;
