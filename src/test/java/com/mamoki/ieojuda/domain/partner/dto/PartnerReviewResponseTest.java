package com.mamoki.ieojuda.domain.partner.dto;

import com.mamoki.ieojuda.domain.account.entity.User;
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

        Evidence evidence = mock(Evidence.class);
        when(evidence.getPlan()).thenReturn(plan);
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

        Evidence evidence = mock(Evidence.class);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.POSTMORTEM_REPORT);

        PartnerReviewResponse response = PartnerReviewResponse.from(evidence);

        assertThat(response.evidenceType()).isEqualTo("POSTMORTEM_REPORT");
    }
}
