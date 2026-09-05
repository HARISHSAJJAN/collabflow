package com.collabflow.websocket;

/**
 * The envelope every {@code /topic/projects/{id}} broadcast uses. Unlike Kafka (which carries
 * Java type headers - see application.yml's comment), STOMP messages here are serialized by a
 * plain Jackson message converter with no type metadata, so an explicit {@code eventType}
 * discriminator is how a frontend client tells one update apart from another.
 */
public record ProjectUpdateMessage(String eventType, Object data) {
}
