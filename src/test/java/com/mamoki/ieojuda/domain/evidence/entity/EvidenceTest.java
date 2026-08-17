package com.mamoki.ieojuda.domain.evidence.entity;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #80 완료 조건 - "삭제 후에도 무결성 해시와 감사 기록은 유지되는지 확인"
class EvidenceTest {

    @Test
    void approve_schedulesDeletionThirtyDaysAfterReview() {
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/1/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-abc").build();

        evidence.approve();

        assertThat(evidence.getDeleteScheduledAt()).isEqualTo(evidence.getReviewedAt().plusDays(30));
    }

    @Test
    void markDeleted_clearsOnlyDeletedAtAndFailureReason_preservesAuditFields() {
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/1/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-abc").build();
        evidence.approve();
        evidence.markDeleteFailed("first attempt failed");

        LocalDateTime submittedAtBefore = evidence.getSubmittedAt();
        LocalDateTime reviewedAtBefore = evidence.getReviewedAt();
        LocalDateTime deleteScheduledAtBefore = evidence.getDeleteScheduledAt();
        EvidenceReviewStatus reviewStatusBefore = evidence.getReviewStatus();
        String integrityHashBefore = evidence.getIntegrityHash();

        evidence.markDeleted();

        // 원본 파일만 지워지는 개념이라 - 영구 감사 기록(명세서 "증빙 보관 및 삭제 정책")은 그대로 남아야 한다
        assertThat(evidence.getDeletedAt()).isNotNull();
        assertThat(evidence.getFailureReason()).isNull();
        assertThat(evidence.getSubmittedAt()).isEqualTo(submittedAtBefore);
        assertThat(evidence.getReviewedAt()).isEqualTo(reviewedAtBefore);
        assertThat(evidence.getDeleteScheduledAt()).isEqualTo(deleteScheduledAtBefore);
        assertThat(evidence.getReviewStatus()).isEqualTo(reviewStatusBefore);
        assertThat(evidence.getIntegrityHash()).isEqualTo(integrityHashBefore);
    }
}
