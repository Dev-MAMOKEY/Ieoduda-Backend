package com.mamoki.ieojuda.domain.plan.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifeAreaSummaryResponseTest {

    private ItemResponse item(String title, String content, String status) {
        return new ItemResponse(UUID.randomUUID(), "target", "location", "action", title, content,
                "", "FAMILY", "excerpt", status, 0, "OTHER", null);
    }

    @Test
    void from_returnsNullSummary_whenNoApprovedItems() {
        LifeAreaResponse lifeAreaResponse = new LifeAreaResponse("FAMILY", List.of(
                item("인스타그램", "탈퇴 처리", "PROPOSED")));

        LifeAreaSummaryResponse response = LifeAreaSummaryResponse.from(lifeAreaResponse);

        assertThat(response.summary()).isNull();
        assertThat(response.itemCount()).isZero();
        assertThat(response.isWritten()).isFalse();
    }

    @Test
    void from_returnsNullSummary_whenNoItemsAtAll() {
        LifeAreaResponse lifeAreaResponse = new LifeAreaResponse("FAMILY", List.of());

        LifeAreaSummaryResponse response = LifeAreaSummaryResponse.from(lifeAreaResponse);

        assertThat(response.summary()).isNull();
        assertThat(response.itemCount()).isZero();
        assertThat(response.isWritten()).isFalse();
    }

    @Test
    void from_joinsApprovedItemTitleAndContent_whenThreeOrFewer() {
        LifeAreaResponse lifeAreaResponse = new LifeAreaResponse("FAMILY", List.of(
                item("인스타그램", "탈퇴 처리", "APPROVED"),
                item("카카오톡", "탈퇴 처리", "APPROVED"),
                item("네이버클라우드", "자료 이전", "PROPOSED")));

        LifeAreaSummaryResponse response = LifeAreaSummaryResponse.from(lifeAreaResponse);

        assertThat(response.summary()).isEqualTo("인스타그램 탈퇴 처리, 카카오톡 탈퇴 처리");
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.isWritten()).isTrue();
    }

    @Test
    void from_limitsToThreeItemsAndAppendsRemainingCount_whenMoreThanThreeApproved() {
        LifeAreaResponse lifeAreaResponse = new LifeAreaResponse("FAMILY", List.of(
                item("A", "정리", "APPROVED"),
                item("B", "정리", "APPROVED"),
                item("C", "정리", "APPROVED"),
                item("D", "정리", "APPROVED"),
                item("E", "정리", "APPROVED")));

        LifeAreaSummaryResponse response = LifeAreaSummaryResponse.from(lifeAreaResponse);

        assertThat(response.summary()).isEqualTo("A 정리, B 정리, C 정리 외 2건");
        assertThat(response.itemCount()).isEqualTo(5);
        assertThat(response.isWritten()).isTrue();
    }

    @Test
    void from_keepsFixedCategoryAndLabel_regardlessOfItems() {
        LifeAreaResponse lifeAreaResponse = new LifeAreaResponse("WORK_CONTINUITY", List.of());

        LifeAreaSummaryResponse response = LifeAreaSummaryResponse.from(lifeAreaResponse);

        assertThat(response.category()).isEqualTo("WORK_CONTINUITY");
        assertThat(response.label()).isEqualTo("업무 처리");
    }
}
