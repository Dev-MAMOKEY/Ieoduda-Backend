package com.mamoki.ieojuda.domain.partner.repository;

import com.mamoki.ieojuda.domain.partner.entity.ExternalPartner;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalPartnerRepository extends JpaRepository<ExternalPartner, UUID> {
}
