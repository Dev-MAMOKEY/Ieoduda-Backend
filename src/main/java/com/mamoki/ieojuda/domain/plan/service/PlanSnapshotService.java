package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.stage.entity.Dependency;
import com.mamoki.ieojuda.domain.stage.repository.DependencyRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.IntegrityHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

// 사건(ReleaseCase)이 열리는 시점의 계획 상태를 얼려 두는 스냅샷 생성/직렬화/역직렬화 담당(issue #42).
// 스냅샷이 봉인된 이후에는 어떤 서비스도 이 스냅샷을 다시 만들지 않고 그대로 읽기만 해야 한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanSnapshotService {

    private final ItemRepository itemRepository;
    private final RecipientRepository recipientRepository;
    private final DependencyRepository dependencyRepository;
    private final ObjectMapper objectMapper;

    public PlanSnapshotDto buildSnapshot(Plan plan) {
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderByItemIdAsc(plan.getPlanId());
        List<Recipient> recipients = recipientRepository.findByPlan_PlanId(plan.getPlanId());
        List<Dependency> dependencies = dependencyRepository.findByPlan_PlanId(plan.getPlanId());
        return PlanSnapshotDto.of(plan, items, recipients, dependencies);
    }

    public String serialize(PlanSnapshotDto snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public PlanSnapshotDto deserialize(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, PlanSnapshotDto.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String hash(String snapshotJson) {
        return IntegrityHasher.sha256Hex(snapshotJson.getBytes(StandardCharsets.UTF_8));
    }
}
