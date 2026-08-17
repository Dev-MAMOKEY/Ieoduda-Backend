package com.mamoki.ieojuda.domain.evidence.entity;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// issue #80 완료 조건 - "삭제 후에도 무결성 해시와 감사 기록은 유지되는지 확인"
class EvidenceTest {

    @Test
    void approve_schedulesDeletionThirtyDaysAfterReview() {
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/1/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-abc").evidenceType(EvidenceType.DEATH_CERTIFICATE).build();

        evidence.approve();

        assertThat(evidence.getDeleteScheduledAt()).isEqualTo(evidence.getReviewedAt().plusDays(30));
    }

    @Test
    void markDeleted_clearsOnlyDeletedAtAndFailureReason_preservesAuditFields() {
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/1/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-abc").evidenceType(EvidenceType.DEATH_CERTIFICATE).build();
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

    private Evidence newEvidence() {
        return Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/1/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-abc").evidenceType(EvidenceType.DEATH_CERTIFICATE).build();
    }

    // issue #45 완료 조건 - 판정(승인/반려)은 서비스 계층 검사와 별개로 엔티티 스스로도 재판정을 거부해야
    // 한다. 서비스 계층을 우회하는 다른 호출 경로가 생기더라도 이 방어선은 그대로 유지된다.
    @Test
    void approve_whenAlreadyApproved_isBlockedFromDecidingAgain() {
        Evidence evidence = newEvidence();
        evidence.approve();

        assertThatThrownBy(evidence::approve)
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DECIDED));
    }

    @Test
    void reject_whenAlreadyRejected_isBlockedFromDecidingAgain() {
        Evidence evidence = newEvidence();
        evidence.reject("사유");

        assertThatThrownBy(() -> evidence.reject("다른 사유"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DECIDED));
    }

    @Test
    void approve_whenAlreadyRejected_isBlocked() {
        Evidence evidence = newEvidence();
        evidence.reject("사유");

        assertThatThrownBy(evidence::approve)
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DECIDED));
    }

    // 추가자료요청은 종결 상태가 아니므로, 그 이후에는 다시 판정할 수 있어야 한다
    @Test
    void approve_afterAdditionalInfoRequested_succeeds() {
        Evidence evidence = newEvidence();
        evidence.reAdditionalInfo();

        evidence.approve();

        assertThat(evidence.getReviewStatus()).isEqualTo(EvidenceReviewStatus.APPROVED);
    }
}
