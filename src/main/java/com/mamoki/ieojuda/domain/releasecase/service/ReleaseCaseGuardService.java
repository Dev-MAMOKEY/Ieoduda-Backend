package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 계정 삭제 시도가 진행 중인 사후 인계 사건 때문에 막힐 때, 그 사건을 동결(freeze)해서 운영 검토로 넘긴다.
// UserService.deleteAccount()는 이 동결 직후 예외를 던져 트랜잭션을 롤백하므로, 동결 자체는 그 롤백과
// 무관하게 남아야 한다 - 그래서 별도 빈으로 분리하고 REQUIRES_NEW로 독립 트랜잭션에 커밋한다.
@Service
@RequiredArgsConstructor
public class ReleaseCaseGuardService {

    private final ReleaseCaseRepository releaseCaseRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean freezeIfActiveCaseExists(Long planId) {
        return releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(planId)
                .filter(this::isActive)
                .map(releaseCase -> {
                    releaseCase.freeze();
                    return true;
                })
                .orElse(false);
    }

    private boolean isActive(ReleaseCase releaseCase) {
        return releaseCase.getStatus() != ReleaseCaseStatus.CANCELED
                && releaseCase.getStatus() != ReleaseCaseStatus.COMPLETED;
    }
}
