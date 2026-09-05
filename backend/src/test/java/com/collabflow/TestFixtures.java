package com.collabflow;

import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.LoginRequest;
import com.collabflow.auth.dto.RegisterRequest;
import com.collabflow.project.ProjectResponse;
import com.collabflow.project.dto.CreateProjectRequest;
import com.collabflow.task.TaskResponse;
import com.collabflow.task.dto.CreateTaskRequest;
import com.collabflow.team.TeamResponse;
import com.collabflow.team.dto.AddMemberRequest;
import com.collabflow.team.dto.CreateTeamRequest;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Shared setup used across integration tests: register+login a user, create a team/project/
 * task. Kept as plain static helpers over {@link TestRestTemplate} (real HTTP calls through
 * the full stack - filters, controllers, services, the database) rather than calling service
 * beans directly, so every test that uses these still exercises the real API surface, not a
 * shortcut around it. Public: used from every integration test package (auth, team, task,
 * notification, ...), not just this one.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public record RegisteredUser(String email, String password, UUID userId, String accessToken, String refreshToken) {
        public HttpHeaders authHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            return headers;
        }
    }

    public static RegisteredUser registerAndLogin(TestRestTemplate rest, String baseUrl, String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@example.com";
        String password = "SuperSecret123";
        rest.postForEntity(baseUrl + "/api/v1/auth/register", new RegisterRequest(email, password, emailPrefix), Void.class);
        AuthResponse login = rest.postForObject(baseUrl + "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
        return new RegisteredUser(email, password, login.userId(), login.accessToken(), login.refreshToken());
    }

    public static TeamResponse createTeam(TestRestTemplate rest, String baseUrl, RegisteredUser owner, String name) {
        HttpEntity<CreateTeamRequest> request = new HttpEntity<>(new CreateTeamRequest(name, null), owner.authHeader());
        return rest.postForObject(baseUrl + "/api/v1/teams", request, TeamResponse.class);
    }

    public static void addTeamMember(TestRestTemplate rest, String baseUrl, RegisteredUser owner, UUID teamId, String memberEmail) {
        HttpEntity<AddMemberRequest> request = new HttpEntity<>(new AddMemberRequest(memberEmail), owner.authHeader());
        rest.postForEntity(baseUrl + "/api/v1/teams/" + teamId + "/members", request, Void.class);
    }

    public static ProjectResponse createProject(TestRestTemplate rest, String baseUrl, RegisteredUser owner, UUID teamId, String name) {
        HttpEntity<CreateProjectRequest> request = new HttpEntity<>(new CreateProjectRequest(teamId, name, null), owner.authHeader());
        return rest.postForObject(baseUrl + "/api/v1/projects", request, ProjectResponse.class);
    }

    public static void addProjectMember(TestRestTemplate rest, String baseUrl, RegisteredUser actor, UUID projectId, UUID userId) {
        HttpEntity<com.collabflow.project.dto.AddProjectMemberRequest> request =
                new HttpEntity<>(new com.collabflow.project.dto.AddProjectMemberRequest(userId), actor.authHeader());
        rest.postForEntity(baseUrl + "/api/v1/projects/" + projectId + "/members", request, Void.class);
    }

    public static TaskResponse createTask(TestRestTemplate rest, String baseUrl, RegisteredUser actor, UUID projectId, String title, UUID assigneeId) {
        HttpEntity<CreateTaskRequest> request =
                new HttpEntity<>(new CreateTaskRequest(projectId, title, null, null, null, assigneeId), actor.authHeader());
        return rest.postForObject(baseUrl + "/api/v1/tasks", request, TaskResponse.class);
    }

    public static <T> org.springframework.http.ResponseEntity<T> get(TestRestTemplate rest, String url, RegisteredUser actor, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(actor.authHeader()), type);
    }
}
