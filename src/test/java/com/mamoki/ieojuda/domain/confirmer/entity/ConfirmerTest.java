package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #82 완료 조건 - "확인자에 대해서도 동일하게 동작한다"
// issue #41 이후 - 초대 토큰 자체는 SecurityToken(서비스 레이어)이 관리하므로, 엔티티 테스트는
// 이메일 변경 시 수락 상태가 초기화되는지만 확인한다 (토큰 폐기는 ConfirmerServiceTest에서 검증)
class ConfirmerTest {

    private Confirmer buildAcceptedConfirmer() {
        Confirmer confirmer = Confirmer.builder()
                .plan(mock(Plan.class)).name("유지민").relationship(Relationship.FRIEND).email("jimin@test.com").build();
        confirmer.markInviteSent();
        confirmer.accept(null);
        return confirmer;
    }

    @Test
    void updateContact_whenEmailChanges_resetsAcceptance() {
        Confirmer confirmer = buildAcceptedConfirmer();

        boolean emailChanged = confirmer.updateContact("유지민", "new-email@test.com");

        assertThat(emailChanged).isTrue();
        assertThat(confirmer.getEmail()).isEqualTo("new-email@test.com");
        assertThat(confirmer.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.PENDING);
        assertThat(confirmer.getAcceptedAt()).isNull();
    }

    @Test
    void updateContact_whenEmailUnchanged_keepsAcceptance() {
        Confirmer confirmer = buildAcceptedConfirmer();

        boolean emailChanged = confirmer.updateContact("이름만변경", "jimin@test.com");

        assertThat(emailChanged).isFalse();
        assertThat(confirmer.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
    }
}
