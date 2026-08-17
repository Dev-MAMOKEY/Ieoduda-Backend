package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #82 완료 조건 - "확인자에 대해서도 동일하게 동작한다"
class ConfirmerTest {

    private Confirmer buildAcceptedConfirmer() {
        Confirmer confirmer = Confirmer.builder()
                .plan(mock(Plan.class)).name("유지민").relationship(Relationship.FRIEND).email("jimin@test.com").build();
        confirmer.issueInviteToken("old-token-hash", LocalDateTime.now().plusHours(72));
        confirmer.accept(null);
        return confirmer;
    }

    @Test
    void updateContact_whenEmailChanges_resetsAcceptanceAndInvalidatesOldToken() {
        Confirmer confirmer = buildAcceptedConfirmer();

        boolean emailChanged = confirmer.updateContact("유지민", "new-email@test.com");

        assertThat(emailChanged).isTrue();
        assertThat(confirmer.getEmail()).isEqualTo("new-email@test.com");
        assertThat(confirmer.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.PENDING);
        assertThat(confirmer.getAcceptedAt()).isNull();
        assertThat(confirmer.getInviteToken()).isNull();
        assertThat(confirmer.getInviteTokenExpiresAt()).isNull();
    }

    @Test
    void updateContact_whenEmailUnchanged_keepsAcceptanceAndToken() {
        Confirmer confirmer = buildAcceptedConfirmer();

        boolean emailChanged = confirmer.updateContact("이름만변경", "jimin@test.com");

        assertThat(emailChanged).isFalse();
        assertThat(confirmer.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
        assertThat(confirmer.getInviteToken()).isEqualTo("old-token-hash");
    }
}
