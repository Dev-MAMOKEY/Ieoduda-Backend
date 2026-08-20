package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.Optional;
import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseCaseDetailResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 관리자가 caseId로 임의의 사건(소유자 무관)을 조회할 수 있는지, 권한 없는 유저와 존재하지 않는 caseId는 거절되는지 검증
class ReleaseCaseDetailServiceTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private ReleaseCaseRepository releaseCaseRepository;
    private PermissionGuard permissionGuard;
    private ReleaseCaseDetailService releaseCaseDetailService;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        permissionGuard = mock(PermissionGuard.class);
        releaseCaseDetailService = new ReleaseCaseDetailService(releaseCaseRepository, permissionGuard);
    }

    @Test
    void CASE_SUPERVISE_권한이_없으면_거절한다() {
        doThrow(new CustomException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionGuard).require(ADMIN_ID, AdminPermission.CASE_SUPERVISE);

        assertThatThrownBy(() -> releaseCaseDetailService.getDetail(ADMIN_ID, CASE_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION);
    }

    @Test
    void 존재하지_않는_caseId면_거절한다() {
        when(releaseCaseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> releaseCaseDetailService.getDetail(ADMIN_ID, CASE_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RELEASE_CASE_NOT_FOUND);
    }

    @Test
    void 소유자가_달라도_관리자는_상세를_조회한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(OWNER_ID);
        when(owner.getEmail()).thenReturn("owner@example.com");
        when(owner.getName()).thenReturn("홍길동");

        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(owner);

        ReleaseCase releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getPlan()).thenReturn(plan);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.WAITING);
        when(releaseCase.getFrozen()).thenReturn(false);

        when(releaseCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(releaseCase));

        ReleaseCaseDetailResponse response = releaseCaseDetailService.getDetail(ADMIN_ID, CASE_ID);

        assertThat(response.caseId()).isEqualTo(CASE_ID);
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.ownerUserId()).isEqualTo(OWNER_ID);
        assertThat(response.ownerEmail()).isEqualTo("owner@example.com");
        assertThat(response.ownerName()).isEqualTo("홍길동");
    }
}
