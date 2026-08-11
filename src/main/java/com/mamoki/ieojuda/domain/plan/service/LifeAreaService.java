package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 명세서 "계획 홈" / "AI 구조화 결과 검토" 화면 - 카테고리(가족/관계 정리/업무 연속성)별 항목 조회
// 항목은 대화 세션(Conversation)을 넘나들며 계속 쌓이고, 같은 카테고리라도 세션마다 LifeArea 행이 새로 생길 수 있어서
// life_id 하나로 조회하지 않고 plan_id + Item.disclosureScope 기준으로 전체를 모아서 카테고리별로 묶는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LifeAreaService {

    private final ItemRepository itemRepository;

    public List<LifeAreaResponse> getLifeAreas(Long planId) {
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderByItemIdAsc(planId);

        Map<LifeAreaCategory, List<Item>> itemsByCategory = items.stream()
                .collect(Collectors.groupingBy(
                        item -> toLifeAreaCategory(item.getDisclosureScope()),
                        () -> new EnumMap<>(LifeAreaCategory.class),
                        Collectors.toList()
                ));

        return Arrays.stream(LifeAreaCategory.values())
                .map(category -> new LifeAreaResponse(
                        category.name(),
                        itemsByCategory.getOrDefault(category, List.of()).stream().map(ItemResponse::from).toList()
                ))
                .toList();
    }

    private LifeAreaCategory toLifeAreaCategory(DisclosureScope disclosureScope) {
        return switch (disclosureScope) {
            case FAMILY -> LifeAreaCategory.FAMILY;
            case WORK -> LifeAreaCategory.WORK_CONTINUITY;
            case RELATIONSHIP -> LifeAreaCategory.RELATIONSHIP_CLEANUP;
        };
    }
}
