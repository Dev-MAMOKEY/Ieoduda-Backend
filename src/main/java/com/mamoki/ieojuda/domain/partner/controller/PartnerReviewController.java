package com.mamoki.ieojuda.domain.partner.controller;

import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewResponse;
import com.mamoki.ieojuda.domain.partner.service.PartnerReviewService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "외부 파트너 증빙 검토" 화면 - 외부 법무·장례 파트너 전용
@Tag(name = "Partner Review", description = "외부 파트너 증빙 검토 - 조회 / 승인·반려·추가자료요청")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/partner/reviews/{reviewId}")
public class PartnerReviewController {

    private final PartnerReviewService partnerReviewService;

    @Operation(summary = "검토 대상 조회", description = "대상자(신고 확인자) 정보와 증빙 메타데이터를 조회합니다. 역할별 패키지는 포함하지 않습니다.")
    @GetMapping
    public ResponseEntity<RsData<PartnerReviewResponse>> getReview(
            @Parameter(description = "검토 ID (증빙 ID)") @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(RsData.success(partnerReviewService.getReview(reviewId)));
    }

    @Operation(summary = "증빙 원본 다운로드")
    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(
            @Parameter(description = "검토 ID (증빙 ID)") @PathVariable Long reviewId
    ) {
        byte[] file = partnerReviewService.getFile(reviewId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @Operation(summary = "검토 결과 제출", description = "승인(APPROVE)/반려(REJECT)/추가자료요청(ADDITIONAL_INFO_REQUESTED) 중 하나를 기록합니다. AI 자동 승인은 없습니다. Idempotency-Key 헤더를 보내면 같은 키의 재전송은 중복 요청(409)으로 응답합니다.")
    @PostMapping("/decision")
    public ResponseEntity<RsData<PartnerReviewResponse>> decide(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "검토 ID (증빙 ID)") @PathVariable Long reviewId,
            @Valid @RequestBody PartnerReviewDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(RsData.success(partnerReviewService.decide(reviewId, userId, request, idempotencyKey)));
    }
}
