/**
 * Audit module: an append-only trail of who did what, when, to which resource, and the
 * old/new value. Owns the {@code audit_logs} table. Populated purely by consuming domain
 * events from Kafka - it never calls into other modules, only listens.
 */
package com.collabflow.audit;
