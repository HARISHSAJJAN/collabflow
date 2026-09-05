package com.collabflow.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import com.collabflow.TestFixtures;
import com.collabflow.TestFixtures.RegisteredUser;
import com.collabflow.task.dto.UpdateTaskRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The project's mandatory concurrency requirement (ADR-006), proven with <b>real threads
 * racing against each other</b> - not two sequential requests, which is what the manual curl
 * verification after Phase 7 used ({@code &} + {@code wait} in bash to get genuine
 * concurrency; see docs/database.md's concurrency section). This test reproduces that same
 * race automatically, as a real regression guard: if optimistic locking were ever accidentally
 * removed or broken, this test - not just a human remembering to check - would fail.
 *
 * <p>Each racer reads the response as a plain {@code String}, not {@code TaskResponse} - found
 * by testing: {@code TestRestTemplate} doesn't throw on a non-2xx status, so a losing racer's
 * 409 response (an {@code ApiError} body, not a {@code TaskResponse}) would otherwise fail
 * Jackson deserialization at the primitive {@code long version} field, which can't be null -
 * masking every conflict as an unrelated {@code RestClientException} instead of the 409 this
 * test exists to observe. Only the status code is needed here; the final state is re-fetched
 * with its real type afterward.</p>
 */
class TaskConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void concurrentUpdatesToTheSameTaskLoseAtMostOneNotBoth() throws InterruptedException {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        var team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Concurrency Team");
        var project = TestFixtures.createProject(restTemplate, baseUrl, owner, team.id(), "Concurrency Project");
        var task = TestFixtures.createTask(restTemplate, baseUrl, owner, project.id(), "Race me", null);

        int racers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch readyLatch = new CountDownLatch(racers);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        for (int i = 0; i < racers; i++) {
            int n = i;
            pool.submit(() -> {
                readyLatch.countDown();
                try {
                    // Every thread waits at the gate so they all fire at once, once released -
                    // this is what makes it a genuine race on the same version, not ten
                    // sequential requests that would trivially never conflict.
                    startLatch.await();
                    HttpEntity<UpdateTaskRequest> request = new HttpEntity<>(
                            new UpdateTaskRequest("Race me - racer " + n, null, null, null), owner.authHeader());
                    ResponseEntity<String> response = restTemplate.exchange(
                            baseUrl + "/api/v1/tasks/" + task.id(), HttpMethod.PATCH, request, String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        succeeded.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // The core guarantee: not everyone can have won. At least one racer must have been
        // told "someone else changed this first" (409), which is exactly what would NOT
        // happen if optimistic locking silently let every concurrent write through and the
        // last one to physically commit won with no one told about it (the "lost update" bug
        // this mechanism exists to prevent).
        assertThat(conflicted.get()).isGreaterThan(0);
        assertThat(succeeded.get()).isGreaterThan(0);
        assertThat(succeeded.get() + conflicted.get()).isEqualTo(racers);

        // And the version actually advanced by exactly the number of writes that really won -
        // not once per attempt, confirming each successful write really did see a fresh version.
        ResponseEntity<TaskResponse> finalState = TestFixtures.get(
                restTemplate, baseUrl + "/api/v1/tasks/" + task.id(), owner, TaskResponse.class);
        assertThat(finalState.getBody().version()).isEqualTo(succeeded.get());
    }
}
