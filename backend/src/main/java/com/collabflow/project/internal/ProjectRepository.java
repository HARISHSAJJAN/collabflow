package com.collabflow.project.internal;

import com.collabflow.project.ProjectStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByTeamId(UUID teamId, Pageable pageable);

    Page<Project> findByTeamIdAndStatus(UUID teamId, ProjectStatus status, Pageable pageable);

    /** Used for a MEMBER's restricted project listing - see {@code ProjectService}'s Javadoc: a MEMBER only sees projects they've been explicitly added to, not every project in the team. */
    @Query("""
            select p from Project p
            where p.teamId = :teamId
              and p.id in (select pm.projectId from ProjectMember pm where pm.userId = :userId)
            """)
    Page<Project> findAccessibleProjectsInTeam(@Param("teamId") UUID teamId, @Param("userId") UUID userId, Pageable pageable);

    /** Same as {@link #findAccessibleProjectsInTeam}, additionally filtered by status. */
    @Query("""
            select p from Project p
            where p.teamId = :teamId
              and p.status = :status
              and p.id in (select pm.projectId from ProjectMember pm where pm.userId = :userId)
            """)
    Page<Project> findAccessibleProjectsInTeam(
            @Param("teamId") UUID teamId, @Param("userId") UUID userId, @Param("status") ProjectStatus status, Pageable pageable);
}
