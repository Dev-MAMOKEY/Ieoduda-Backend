package com.mamoki.ieojuda.domain.releasecase.repository;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseCaseRepository extends JpaRepository<ReleaseCase, Long> {
}
