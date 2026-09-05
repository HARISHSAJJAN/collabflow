package com.collabflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import com.collabflow.TestFixtures;
import com.collabflow.TestFixtures.RegisteredUser;
import com.collabflow.task.TaskResponse;
import com.collabflow.task.dto.AssignTaskRequest;
import com.collabflow.task.dto.UpdateTaskRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Cross-cutting authentication/authorization boundaries that don't belong to any one module's
 * own test class: missing/malformed/tampered credentials (config.JwtAuthenticationFilter), and
 * a MEMBER overstepping the task-assignment policy that is unique to tasks (only ADMIN+ may
 * assign; a MEMBER may only touch tasks assigned to them - see TaskService's class Javadoc).
 * Deliberately representative, not exhaustive: every one of the ~30 endpoints going through the
 * same filter chain and the same handful of authorization primitives is not independently
 * re-tested here (see docs/testing.md for the scope rationale).
 */
class SecurityBoundaryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aRequestWithNoAuthorizationHeaderIsRejected() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/api/v1/teams/" + UUID.randomUUID(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aStructurallyInvalidTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-jwt");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenWithATamperedSignatureIsRejected() {
        RegisteredUser user = TestFixtures.registerAndLogin(restTemplate, baseUrl, "victim");
        // Flip the last character of the signature segment - still well-formed (three
        // dot-separated base64url segments), but must fail HS256 signature verification.
        String[] parts = user.accessToken().split("\\.");
        char last = parts[2].charAt(parts[2].length() - 1);
        char flipped = last == 'A' ? 'B' : 'A';
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 1) + flipped;
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tampered);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMemberCannotAssignATaskToThemselvesOrAnyoneElse() {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        RegisteredUser member = TestFixtures.registerAndLogin(restTemplate, baseUrl, "member");
        var team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Boundary Team");
        TestFixtures.addTeamMember(restTemplate, baseUrl, owner, team.id(), member.email());
        var project = TestFixtures.createProject(restTemplate, baseUrl, owner, team.id(), "Boundary Project");
        TestFixtures.addProjectMember(restTemplate, baseUrl, owner, project.id(), member.userId());
        var task = TestFixtures.createTask(restTemplate, baseUrl, owner, project.id(), "Unassigned work", null);

        HttpEntity<AssignTaskRequest> selfAssign = new HttpEntity<>(new AssignTaskRequest(member.userId()), member.authHeader());
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/v1/tasks/" + task.id() + "/assignee", HttpMethod.PATCH, selfAssign, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aMemberNotAssignedToATaskCannotEditItEvenThoughTheyCanSeeIt() {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        RegisteredUser assignee = TestFixtures.registerAndLogin(restTemplate, baseUrl, "assignee");
        RegisteredUser bystander = TestFixtures.registerAndLogin(restTemplate, baseUrl, "bystander");
        var team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Assignee Team");
        TestFixtures.addTeamMember(restTemplate, baseUrl, owner, team.id(), assignee.email());
        TestFixtures.addTeamMember(restTemplate, baseUrl, owner, team.id(), bystander.email());
        var project = TestFixtures.createProject(restTemplate, baseUrl, owner, team.id(), "Assignee Project");
        TestFixtures.addProjectMember(restTemplate, baseUrl, owner, project.id(), assignee.userId());
        TestFixtures.addProjectMember(restTemplate, baseUrl, owner, project.id(), bystander.userId());
        var task = TestFixtures.createTask(restTemplate, baseUrl, owner, project.id(), "Assigned to someone else", assignee.userId());

        // The bystander CAN see it (they're a project member)...
        ResponseEntity<TaskResponse> read = TestFixtures.get(
                restTemplate, baseUrl + "/api/v1/tasks/" + task.id(), bystander, TaskResponse.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ...but cannot edit it, since they are neither ADMIN/OWNER nor the assignee.
        HttpEntity<UpdateTaskRequest> editAttempt = new HttpEntity<>(
                new UpdateTaskRequest("Hijacked title", null, null, null), bystander.authHeader());
        ResponseEntity<Map> edit = restTemplate.exchange(
                baseUrl + "/api/v1/tasks/" + task.id(), HttpMethod.PATCH, editAttempt, Map.class);
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
