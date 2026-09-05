package com.collabflow.team.internal;

import com.collabflow.team.TeamRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    List<TeamMember> findByTeamIdOrderByJoinedAt(UUID teamId);

    Page<TeamMember> findByUserId(UUID userId, Pageable pageable);

    long countByTeamIdAndRole(UUID teamId, TeamRole role);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);
}
