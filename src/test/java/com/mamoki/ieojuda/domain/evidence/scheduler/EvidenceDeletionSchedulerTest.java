package com.mamoki.ieojuda.domain.evidence.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 명세서 "증빙 삭제 감사" - 30일 초과 미삭제 증빙은 보안 경보로 처리해야 하므로, 한 건의 삭제 실패가
// 다른 건의 성공적인 삭제까지 막아서는 안 된다(배치 트랜잭션 하나로 묶여 있기 때문에 특히 중요).
class EvidenceDeletionSchedulerTest {

    private EvidenceRepository evidenceRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private AdminActionAuditService adminActionAuditService;
    private EvidenceDeletionScheduler scheduler;

    @BeforeEach
    void setUp() {
        evidenceRepository = mock(EvidenceRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        scheduler = new EvidenceDeletionScheduler(evidenceRepository, evidenceStorageClient, adminActionAuditService);
    }

    @Test
    void deleteExpiredEvidences_whenNoneAreDue_doesNothing() {
        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any())).thenReturn(List.of());

        scheduler.deleteExpiredEvidences();

        verifyNoInteractions(evidenceStorageClient, adminActionAuditService);
    }

    @Test
    void deleteExpiredEvidences_whenStorageDeleteSucceeds_marksDeletedWithoutAuditingSuccess() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getStorageKey()).thenReturn("evidence/1/uuid.pdf");
        when(evidence.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(1));
        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any())).thenReturn(List.of(evidence));

        scheduler.deleteExpiredEvidences();

        verify(evidenceStorageClient).delete("evidence/1/uuid.pdf");
        verify(evidence).markDeleted();
        verify(evidence, never()).markDeleteFailed(anyString());
        // 성공 건은 감사 로그를 남기지 않는다 - "증빙 삭제 감사" 화면이 deletedAt을 직접 노출하므로 중복 기록을 피한다.
        verifyNoInteractions(adminActionAuditService);
    }

    @Test
    void deleteExpiredEvidences_whenStorageDeleteFails_marksFailedAndAudits() {
        UUID evidenceId = UUID.randomUUID();
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(evidenceId);
        when(evidence.getStorageKey()).thenReturn("evidence/5/uuid.pdf");
        when(evidence.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(1));
        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any())).thenReturn(List.of(evidence));
        doThrow(new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED))
                .when(evidenceStorageClient).delete("evidence/5/uuid.pdf");

        scheduler.deleteExpiredEvidences();

        verify(evidence, never()).markDeleted();
        verify(evidence).markDeleteFailed(anyString());
        verify(adminActionAuditService).recordSystem(eq(AdminActionType.EVIDENCE_DELETE), eq(evidenceId), eq(false), anyString());
    }

    @Test
    void deleteExpiredEvidences_whenOneOfSeveralFails_theOthersStillGetDeleted() {
        Evidence failing = mock(Evidence.class);
        when(failing.getEvidenceId()).thenReturn(UUID.randomUUID());
        when(failing.getStorageKey()).thenReturn("evidence/1/fail.pdf");
        when(failing.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(1));
        doThrow(new RuntimeException("boom")).when(evidenceStorageClient).delete("evidence/1/fail.pdf");

        Evidence succeeding = mock(Evidence.class);
        when(succeeding.getEvidenceId()).thenReturn(UUID.randomUUID());
        when(succeeding.getStorageKey()).thenReturn("evidence/2/ok.pdf");
        when(succeeding.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(1));

        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any()))
                .thenReturn(List.of(failing, succeeding));

        scheduler.deleteExpiredEvidences();

        verify(failing).markDeleteFailed(anyString());
        verify(succeeding).markDeleted();
        verify(succeeding, never()).markDeleteFailed(anyString());
    }

    // issue #80 완료 조건 - "30일 초과 미삭제 건이 경보로 감지된다"
    @Test
    void deleteExpiredEvidences_whenScheduledDatePlus30DaysHasPassed_logsSecurityAlert() {
        UUID evidenceId = UUID.randomUUID();
        Evidence severelyOverdue = mock(Evidence.class);
        when(severelyOverdue.getEvidenceId()).thenReturn(evidenceId);
        when(severelyOverdue.getStorageKey()).thenReturn("evidence/9/old.pdf");
        when(severelyOverdue.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(31));
        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any())).thenReturn(List.of(severelyOverdue));

        List<ILoggingEvent> logs = captureLogs(() -> scheduler.deleteExpiredEvidences());

        assertThat(logs).anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("[SECURITY ALERT]")
                && event.getFormattedMessage().contains("evidenceId=" + evidenceId));
    }

    @Test
    void deleteExpiredEvidences_whenWithinThirtyDayGracePeriod_doesNotLogSecurityAlert() {
        Evidence recentlyDue = mock(Evidence.class);
        when(recentlyDue.getEvidenceId()).thenReturn(UUID.randomUUID());
        when(recentlyDue.getStorageKey()).thenReturn("evidence/10/recent.pdf");
        when(recentlyDue.getDeleteScheduledAt()).thenReturn(LocalDateTime.now().minusDays(10)); // 삭제 예정일은 지났지만 +30일 유예 안
        when(evidenceRepository.findDueForDeletionForUpdateSkipLocked(any())).thenReturn(List.of(recentlyDue));

        List<ILoggingEvent> logs = captureLogs(() -> scheduler.deleteExpiredEvidences());

        assertThat(logs).noneMatch(event -> event.getFormattedMessage().contains("[SECURITY ALERT]"));
    }

    private List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(EvidenceDeletionScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
