package com.mamoki.ieojuda.domain.releasecase.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// issue #45 완료 조건 - "취소·분쟁 사건이 파트너 재판정으로 WAITING에 되살아나거나 상태가 반복·역행하지
// 않는다"를 엔티티 레벨에서 직접 검증한다. 이 상태 전이표 강제가 곧 issue #45의 핵심 취약점 수정이다.
class ReleaseCaseTest {

    private ReleaseCase newCase() {
        return ReleaseCase.builder().plan(mock(Plan.class)).planVersion(mock(PlanVersion.class)).build();
    }

    @Test
    void fullLifecycle_transitionsInOrder_succeed() {
        ReleaseCase releaseCase = newCase();

        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.approveEvidenceAndStartWaiting(7);
        releaseCase.startReleasing();
        releaseCase.complete();

        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.COMPLETED);
        assertThat(releaseCase.getCompletedAt()).isNotNull();
    }

    // issue #45 핵심 공격 시나리오 - 작성자가 취소한 사건을, 뒤늦게 도착한(또는 재시도된) 파트너 승인
    // 요청이 WAITING으로 되살리면 안 된다.
    @Test
    void approveEvidenceAndStartWaiting_whenCaseAlreadyCanceled_isBlocked() {
        ReleaseCase releaseCase = newCase();
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.cancel();

        assertThatThrownBy(() -> releaseCase.approveEvidenceAndStartWaiting(7))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION));
        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.CANCELED);
    }

    // 이의 제기로 절차가 멈춘 사건도 같은 이유로 재판정만으로 WAITING에 되돌아가면 안 된다
    // (분쟁 해소는 별도 관리자 절차를 거쳐야 한다 - 이 엔티티에는 그 재개 경로 자체가 없다).
    @Test
    void approveEvidenceAndStartWaiting_whenCaseDisputed_isBlocked() {
        ReleaseCase releaseCase = newCase();
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.approveEvidenceAndStartWaiting(7);
        releaseCase.raiseDispute();

        assertThatThrownBy(() -> releaseCase.approveEvidenceAndStartWaiting(7))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION));
        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.DISPUTED);
    }

    @Test
    void cancel_afterReleasingStarted_isBlocked() {
        ReleaseCase releaseCase = newCase();
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.approveEvidenceAndStartWaiting(7);
        releaseCase.startReleasing();

        assertThatThrownBy(releaseCase::cancel)
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION));
    }

    @Test
    void startReleasing_whenNotWaiting_isBlocked() {
        ReleaseCase releaseCase = newCase();

        assertThatThrownBy(() -> releaseCase.startReleasing())
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION));
    }

    @Test
    void complete_whenNotReleasing_isBlocked() {
        ReleaseCase releaseCase = newCase();

        assertThatThrownBy(releaseCase::complete)
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION));
    }

    // 부분 승인(EVIDENCE_APPROVED) 상태에서도 반려로 넘어갈 수 있어야 한다 - 한쪽 증빙만 승인된 뒤
    // 나머지 한쪽이 반려되는 정상 시나리오다.
    @Test
    void rejectEvidence_fromPartiallyApproved_succeeds() {
        ReleaseCase releaseCase = newCase();
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.markEvidencePartiallyApproved();

        releaseCase.rejectEvidence();

        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.EVIDENCE_REJECTED);
    }
}
