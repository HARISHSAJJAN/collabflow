package com.collabflow.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry and dead-letter policy for every {@code @KafkaListener} in the app. Spring Boot's
 * autoconfiguration picks up this single {@link CommonErrorHandler} bean and wires it into
 * the listener container factory it builds from {@code application.yml}'s {@code spring.
 * kafka.*} properties - no need to hand-build the container factory just for this.
 *
 * <p><b>Retry and dead-letter policy</b>: if a listener method throws, the message is
 * retried up to 3 times with a 1-second delay between attempts (still on the same consumer
 * thread, blocking that partition's progress briefly - see docs/kafka.md for why that
 * trade-off is acceptable at this project's throughput). If all retries fail, the message is
 * published to a dead-letter topic (the original topic name + {@code .DLT}, e.g.
 * {@code collabflow.task-events.DLT}) via {@link DeadLetterPublishingRecoverer}, and the
 * original offset is committed so the consumer moves on rather than getting stuck retrying
 * the same poison message forever. Nothing currently reads the DLT topics - see
 * docs/kafka.md for why that's a documented gap, not a hidden one.</p>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        FixedBackOff backOff = new FixedBackOff(1000L, 3);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
