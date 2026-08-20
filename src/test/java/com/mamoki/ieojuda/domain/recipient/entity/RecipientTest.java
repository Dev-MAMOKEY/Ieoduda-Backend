package com.mamoki.ieojuda.domain.recipient.entity;

import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #82 완료 조건 - "담당자 이메일을 바꾸면 수락 상태가 초기화된다"
// issue #41 이후 - 초대 토큰 자체는 SecurityToken(서비스 레이어)이 관리하므로, 엔티티 테스트는
// 이메일 변경 시 수락 상태가 초기화되는지만 확인한다 (토큰 폐기는 RecipientServiceTest에서 검증)
class RecipientTest {

    private Recipient buildAcceptedRecipient() {
        Recipient recipient = Recipient.builder()
                .plan(mock(Plan.class)).lifeArea(mock(LifeArea.class))
                .name("이지수").email("jisoo@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build();
        recipient.markInviteSent();
        recipient.accept(null);
        return recipient;
    }

    @Test
    void updateContact_whenEmailChanges_resetsAcceptance() {
        Recipient recipient = buildAcceptedRecipient();

        boolean emailChanged = recipient.updateContact("이지수", "new-email@test.com");

        assertThat(emailChanged).isTrue();
        assertThat(recipient.getEmail()).isEqualTo("new-email@test.com");
        assertThat(recipient.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.PENDING);
        assertThat(recipient.getAcceptedAt()).isNull();
    }

    @Test
    void updateContact_whenEmailUnchanged_keepsAcceptance() {
        Recipient recipient = buildAcceptedRecipient();

        boolean emailChanged = recipient.updateContact("이름만변경", "jisoo@test.com");

        assertThat(emailChanged).isFalse();
        assertThat(recipient.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
    }
}
