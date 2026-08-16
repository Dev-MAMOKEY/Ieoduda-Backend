package com.mamoki.ieojuda.global.validation;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// issue #62 회귀 테스트 - role_assigness/assigne_id 오탈자가 다시 들어오지 않는지 확인한다.
// 실수로 오탈자 표기를 되살리면 DB의 실제 컬럼/테이블명과 엔티티 매핑이 어긋나 부팅 시점에 조용히
// 깨지거나(ddl-auto=update가 새 컬럼을 만들어버림) 최악의 경우 데이터가 갈라질 수 있다.
class SchemaNamingContractTest {

    @Test
    void recipientTableNameHasNoTypo() {
        Table table = Recipient.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("role_assignees");
    }

    @Test
    void recipientForeignKeyColumnsHaveNoTypo() throws NoSuchFieldException {
        Map<Class<?>, String> recipientFieldByType = Map.of(
                HandoverStage.class, "recipient",
                HandoffCheckResponse.class, "recipient",
                PackageIssue.class, "recipient",
                Item.class, "recipient"
        );

        for (Map.Entry<Class<?>, String> entry : recipientFieldByType.entrySet()) {
            Class<?> type = entry.getKey();
            JoinColumn joinColumn = type.getDeclaredField(entry.getValue()).getAnnotation(JoinColumn.class);
            assertThat(joinColumn.name())
                    .as("%s.%s foreign key column name", type.getSimpleName(), entry.getValue())
                    .isEqualTo("assignee_id");
        }
    }
}
