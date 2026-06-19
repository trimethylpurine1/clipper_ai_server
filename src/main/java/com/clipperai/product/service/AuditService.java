package com.clipperai.product.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.clipperai.product.entity.*;
import lombok.*;
import com.clipperai.product.repository.*;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void recordUserAction(
            AppUser actor,
            AuditAction action,
            String targetType,
            UUID targetId,
            String ipAddress,
            String userAgent,
            Map<String, Object> metadata
    ) {
        AuditLog log = AuditLog.builder()
                .actorUser(actor)
                .actorType(AuditActorType.USER)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();

        auditLogRepository.save(log);
    }
    
    public void recordSystemAction(
            AuditAction action,
            String targetType,
            UUID targetId,
            String ipAddress,
            String userAgent,
            Map<String, Object> metadata
    ) {
        AuditLog log = AuditLog.builder()
                .actorType(AuditActorType.SYSTEM)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();

        auditLogRepository.save(log);
    }

    public void recordWorkerAction(
            AuditAction action,
            String targetType,
            UUID targetId,
            UUID jobId,
            Map<String, Object> metadata
    ) {
        AuditLog log = AuditLog.builder()
                .actorType(AuditActorType.WORKER)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .jobId(jobId)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();

        auditLogRepository.save(log);
    }
    
    public void recordWebhookAction(
            AuditAction action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        AuditLog log = AuditLog.builder()
                .actorType(AuditActorType.WEBHOOK)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(metadata == null ? Map.of() : metadata)
                .build();

        auditLogRepository.save(log);
    }
}