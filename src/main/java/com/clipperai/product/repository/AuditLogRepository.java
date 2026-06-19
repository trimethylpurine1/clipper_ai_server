package com.clipperai.product.repository;

import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByActorUserIdOrderByCreatedAtDesc(String actorUserId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, UUID targetId);

    List<AuditLog> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}