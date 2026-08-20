package com.mamoki.ieojuda.domain.partner.dto;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #62 회귀 테스트 - 파트너 검토 응답의 이름은 신고한 확인자가 아니라 사망 확인 대상자
// (계획 작성자 본인)의 것이어야 한다. "신고자와의 관계" 필드는 대상자 본인에게는 의미가 없어 제거됨.
class PartnerReviewResponseTest {

    @Test
    void from_usesThePlanOwnersNameNotTheReportingConfirmers() {
        User planOwner = mock(User.class);
        when(planOwner.getName()).thenReturn("대상자 본인");
        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(planOwner);

        Confirmer confirmer = mock(Confirmer.class);
        Evidence evidence = mock(Evidence.class);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getConfirmer()).thenReturn(confirmer);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.DEATH_CERTIFICATE);

        PartnerReviewResponse response = PartnerReviewResponse.from(evidence);

        assertThat(response.targetName()).isEqualTo("대상자 본인");
    }

    // issue #88 완료 조건 - 파트너 검토 화면에서 증빙 종류를 확인할 수 있어야 한다
    @Test
    void from_includesEvidenceType() {
        User planOwner = mock(User.class);
        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(planOwner);

        Confirmer confirmer = mock(Confirmer.class);
        Evidence evidence = mock(Evidence.class);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getConfirmer()).thenReturn(confirmer);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.POSTMORTEM_REPORT);

        PartnerReviewResponse response = PartnerReviewResponse.from(evidence);

        assertThat(response.evidenceType()).isEqualTo("POSTMORTEM_REPORT");
    }

    // 파트너 화면에서 "누가 신고했는지"(제출자)를 보여줘야 하므로 확인자 이름을 응답에 포함한다
    @Test
    void from_includesConfirmerName() {
        User planOwner = mock(User.class);
        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(planOwner);

        Confirmer confirmer = mock(Confirmer.class);
        when(confirmer.getName()).thenReturn("제출자 확인자");
        Evidence evidence = mock(Evidence.class);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getConfirmer()).thenReturn(confirmer);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.DEATH_CERTIFICATE);

        PartnerReviewResponse response = PartnerReviewResponse.from(evidence);

        assertThat(response.confirmerName()).isEqualTo("제출자 확인자");
    }
}
