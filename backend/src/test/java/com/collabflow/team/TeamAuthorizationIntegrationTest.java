package com.collabflow.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import com.collabflow.TestFixtures;
import com.collabflow.TestFixtures.RegisteredUser;
import com.collabflow.team.dto.ChangeRoleRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@code TeamService}'s authorization policy and its invariant ("a team always has at least
 * one OWNER"), exercised over real HTTP - the automated form of the manual curl verification
 * done after Phase 5 (see docs/architecture.md's phase log).
 */
class TeamAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aNonMemberGetsNotFoundNotForbidden() {
        // See TeamService's Javadoc: team existence is not revealed to non-members - a 404,
        // identical to a nonexistent id, not a 403 that would confirm the team is real.
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        RegisteredUser outsider = TestFixtures.registerAndLogin(restTemplate, baseUrl, "outsider");
        TeamResponse team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Private Team");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id(), HttpMethod.GET, new HttpEntity<>(outsider.authHeader()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMemberCannotInviteOthersOrDeleteTheTeam() {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        RegisteredUser member = TestFixtures.registerAndLogin(restTemplate, baseUrl, "member");
        TeamResponse team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "RBAC Team");
        TestFixtures.addTeamMember(restTemplate, baseUrl, owner, team.id(), member.email());

        ResponseEntity<Map> inviteAttempt = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id() + "/members", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "someone-else@example.com"), member.authHeader()), Map.class);
        assertThat(inviteAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> deleteAttempt = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id(), HttpMethod.DELETE, new HttpEntity<>(member.authHeader()), Map.class);
        assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theLastOwnerCanNeitherBeRemovedNorDemoted() {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        TeamResponse team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Solo Team");

        ResponseEntity<Map> removeSelf = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id() + "/members/" + owner.userId(),
                HttpMethod.DELETE, new HttpEntity<>(owner.authHeader()), Map.class);
        assertThat(removeSelf.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> demoteSelf = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id() + "/members/" + owner.userId(),
                HttpMethod.PATCH, new HttpEntity<>(new ChangeRoleRequest(TeamRole.MEMBER), owner.authHeader()), Map.class);
        assertThat(demoteSelf.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void afterPromotingASecondOwnerTheFirstCanLeave() {
        RegisteredUser owner = TestFixtures.registerAndLogin(restTemplate, baseUrl, "owner");
        RegisteredUser future = TestFixtures.registerAndLogin(restTemplate, baseUrl, "future-owner");
        TeamResponse team = TestFixtures.createTeam(restTemplate, baseUrl, owner, "Succession Team");
        TestFixtures.addTeamMember(restTemplate, baseUrl, owner, team.id(), future.email());

        restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id() + "/members/" + future.userId(),
                HttpMethod.PATCH, new HttpEntity<>(new ChangeRoleRequest(TeamRole.OWNER), owner.authHeader()), TeamMemberResponse.class);

        ResponseEntity<Void> removeOriginalOwner = restTemplate.exchange(
                baseUrl + "/api/v1/teams/" + team.id() + "/members/" + owner.userId(),
                HttpMethod.DELETE, new HttpEntity<>(owner.authHeader()), Void.class);
        assertThat(removeOriginalOwner.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
