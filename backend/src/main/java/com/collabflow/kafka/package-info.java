/**
 * Kafka infrastructure shared by every module that publishes or consumes domain events:
 * {@code ProducerFactory}/{@code ConsumerFactory} configuration, topic naming, the common
 * event envelope, and error-handling / dead-letter wiring.
 *
 * <p>Domain modules (task, project, notification, audit, ...) depend on this module to
 * publish events and to register listeners, so it is marked OPEN. The event <em>payload</em>
 * types themselves still live inside their owning domain module and are exposed only through
 * a {@code @NamedInterface}, so this module does not become a dumping ground for domain
 * logic - it only carries the transport mechanics.</p>
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.collabflow.kafka;
