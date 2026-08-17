package com.mamoki.ieojuda.domain.recipient.entity;

import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #82 완료 조건 - "담당자 이메일을 바꾸면 수락 상태가 초기화된다" / "변경 전에 발급된 링크로는
// 더 이상 수락할 수 없다"
class RecipientTest {

    private Recipient buildAcceptedRecipient() {
        Recipient recipient = Recipient.builder()
                .plan(mock(Plan.class)).lifeArea(mock(LifeArea.class))
                .name("이지수").email("jisoo@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build();
        recipient.issueInviteToken("old-token-hash", LocalDateTime.now().plusHours(72));
        recipient.accept(null);
        return recipient;
    }

    @Test
    void updateContact_whenEmailChanges_resetsAcceptanceAndInvalidatesOldToken() {
        Recipient recipient = buildAcceptedRecipient();

        boolean emailChanged = recipient.updateContact("이지수", "new-email@test.com");

        assertThat(emailChanged).isTrue();
        assertThat(recipient.getEmail()).isEqualTo("new-email@test.com");
        assertThat(recipient.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.PENDING);
        assertThat(recipient.getAcceptedAt()).isNull();
        assertThat(recipient.getInviteToken()).isNull(); // 변경 전 발급된 링크로는 더 이상 수락 불가
        assertThat(recipient.getInviteTokenExpiresAt()).isNull();
    }

    @Test
    void updateContact_whenEmailUnchanged_keepsAcceptanceAndToken() {
        Recipient recipient = buildAcceptedRecipient();

        boolean emailChanged = recipient.updateContact("이름만변경", "jisoo@test.com");

        assertThat(emailChanged).isFalse();
        assertThat(recipient.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
        assertThat(recipient.getInviteToken()).isEqualTo("old-token-hash");
    }
}
