package com.mamoki.ieojuda.domain.partner.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewListResponse;
import com.mamoki.ieojuda.domain.partner.service.PartnerReviewService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "외부 파트너 증빙 검토" 화면 - 외부 법무·장례 파트너 전용
// issue #87 - 검토 대상 목록 조회. 배정된 파트너와 무관하게 EVIDENCE_REVIEW 권한만 있으면 전체를 조회한다.
@Tag(name = "Partner Review", description = "외부 파트너 증빙 검토 - 조회 / 승인·반려·추가자료요청")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/partner/reviews")
public class PartnerReviewListController {

    private final PartnerReviewService partnerReviewService;

    @Operation(summary = "검토 목록 조회", description = "EVIDENCE_REVIEW 권한을 가진 검토자가 전체 검토 대상을 상태별로 조회합니다. 역할별 패키지는 포함하지 않습니다.")
    @GetMapping
    public ResponseEntity<RsData<PartnerReviewListResponse>> getReviews(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "검토 상태 필터") @RequestParam(required = false) EvidenceReviewStatus status
    ) {
        return ResponseEntity.ok(RsData.success(partnerReviewService.getReviews(userId, status)));
    }
}
