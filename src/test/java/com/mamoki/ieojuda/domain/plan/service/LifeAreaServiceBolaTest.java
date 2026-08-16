package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 사용자 B가 사용자 A의 planId로 삶의 구역별 항목 조회를 시도하면 PLAN_NOT_FOUND로 막혀야 한다
class LifeAreaServiceBolaTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long PLAN_ID = 10L;

    private PlanRepository planRepository;
    private ItemRepository itemRepository;
    private LifeAreaService lifeAreaService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        itemRepository = mock(ItemRepository.class);
        lifeAreaService = new LifeAreaService(itemRepository, new PlanOwnershipReader(planRepository));
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void getLifeAreasRejectsNonOwnerAndDoesNotDumpItems() {
        assertThatThrownBy(() -> lifeAreaService.getLifeAreas(ATTACKER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void getLifeAreasReturnsItemsForTheOwner() {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID))
                .thenReturn(Optional.of(mock(com.mamoki.ieojuda.domain.plan.entity.Plan.class)));
        when(itemRepository.findByLifeArea_Plan_PlanIdOrderByItemIdAsc(PLAN_ID)).thenReturn(List.of());

        assertThat(lifeAreaService.getLifeAreas(OWNER_ID, PLAN_ID)).isNotEmpty();
    }
}
