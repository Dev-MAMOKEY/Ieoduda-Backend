package com.mamoki.ieojuda.domain.partner.repository;

import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartnerReviewerRepository extends JpaRepository<PartnerReviewer, Long> {

    // 검토 결과 제출 시 요청 바디가 아니라 로그인한 계정(principal)에서 검토자를 찾기 위한 조회
    Optional<PartnerReviewer> findByUser_UserId(Long userId);
}
