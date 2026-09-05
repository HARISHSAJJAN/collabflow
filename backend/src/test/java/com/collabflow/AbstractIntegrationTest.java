package com.collabflow;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every integration test: a real Spring context on a random port, talking to
 * real Postgres/Redis/Kafka - each in a throwaway Testcontainer, never the developer's own
 * {@code docker-compose.yml} services, so tests are hermetic (no risk of test data leaking
 * into local dev state, or local dev state making a test pass/fail depending on what's
 * already in it) and work identically in CI, where no such dev stack exists at all.
 *
 * <p>Containers are declared as {@code static} fields on this base class, which is
 * Testcontainers' standard "singleton container" pattern: JUnit starts them once for the
 * whole test JVM (not once per test class), and every subclass shares the same running
 * containers - the alternative (fresh containers per class) would make a test run take
 * several times longer for no correctness benefit here, since tests don't leak meaningful
 * state into each other at the container level (each test creates its own users/teams/etc.
 * with random emails).</p>
 *
 * <p>All three containers (not just Postgres) for every integration test, even ones that
 * don't obviously need Redis or Kafka: Redis-backed caching/rate-limiting and Kafka
 * publishing are both designed to fail open (see {@code RedisCacheService} and
 * {@code DomainEventPublisher}'s Javadoc) rather than fail the request, so running without
 * them wouldn't break most tests - but it would silently skip exercising real code paths
 * (cache population, event publication) that several tests specifically assert on.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // org.testcontainers.kafka.KafkaContainer (not the older org.testcontainers.containers one)
    // - the older class only accepts confluentinc/cp-kafka images unless explicitly told a
    // different image is compatible; this one natively understands the apache/kafka image
    // that docker-compose.yml also runs, matching prod for real.
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // A fixed, obviously-not-a-real-secret test value - see JwtService's Javadoc for why
        // application.yml has no default for this in every other profile.
        registry.add("collabflow.jwt.secret", () -> "test-only-secret-do-not-use-in-any-real-environment-0123456789");
        registry.add("collabflow.jwt.access-token-ttl-minutes", () -> "15");
        registry.add("collabflow.jwt.refresh-token-ttl-days", () -> "30");
        registry.add("collabflow.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("collabflow.rate-limit.login.capacity", () -> "1000"); // high in tests - rate limiting itself is tested separately, in isolation
        registry.add("collabflow.rate-limit.login.window-seconds", () -> "60");
        registry.add("collabflow.rate-limit.login-by-email.capacity", () -> "1000");
        registry.add("collabflow.rate-limit.login-by-email.window-seconds", () -> "60");
        registry.add("collabflow.rate-limit.register.capacity", () -> "1000");
        registry.add("collabflow.rate-limit.register.window-seconds", () -> "3600");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String baseUrl;

    @BeforeEach
    void setUpBaseUrl() {
        baseUrl = "http://localhost:" + port;
    }

    /** A random-enough email per test to avoid unique-constraint collisions across tests sharing one database. */
    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID() + "@example.com";
    }
}
