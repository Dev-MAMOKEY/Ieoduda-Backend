package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanVersion extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "version_num")
    private Integer versionNum;

    @Column(name = "snapshot_data", columnDefinition = "TEXT")
    private String snapshotData;

    @Column(name = "is_sealed")
    private Boolean isSealed;

    // 스냅샷 원문(snapshotData)의 SHA-256 해시 - 이후 스냅샷이 변조 없이 그대로인지 검증하는 용도
    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    @Column(name = "sealed_at")
    private LocalDateTime sealedAt;

    @Builder
    public PlanVersion(Plan plan, Integer versionNum, String snapshotData) {
        this.plan = plan;
        this.versionNum = versionNum;
        this.snapshotData = snapshotData;
        this.isSealed = false;
    }

    // 스냅샷 데이터를 확정하고, 이후 review·dispatch 파이프라인이 이 값을 신뢰할 수 있도록 봉인한다
    public void seal(String snapshotHash) {
        this.snapshotHash = snapshotHash;
        this.sealedAt = LocalDateTime.now();
        this.isSealed = true;
    }
}
