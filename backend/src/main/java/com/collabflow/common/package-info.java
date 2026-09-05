/**
 * Shared kernel: base entities, cross-cutting DTOs (pagination, API error envelope),
 * common exception types, and generic utilities used by every domain module.
 *
 * <p>Marked as an OPEN Spring Modulith module: unlike domain modules (task, project, ...),
 * which may only be accessed through their exposed API, every other module is allowed to
 * depend on anything in this package. This is a deliberate, narrow exception - a shared
 * kernel is a recognized pattern for the small amount of truly generic code (e.g. a
 * {@code BaseEntity} with id/version/timestamps) that would otherwise be duplicated in
 * every module.</p>
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.collabflow.common;
