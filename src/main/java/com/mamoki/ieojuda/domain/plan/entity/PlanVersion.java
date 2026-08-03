package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plan_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanVersion extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "version_num")
    private Integer versionNum;

    @Column(name = "snapshot_data", columnDefinition = "TEXT")
    private String snapshotData;

    @Column(name = "is_sealed")
    private Boolean isSealed;

    @Builder
    public PlanVersion(Plan plan, Integer versionNum, String snapshotData) {
        this.plan = plan;
        this.versionNum = versionNum;
        this.snapshotData = snapshotData;
        this.isSealed = false;
    }

    public void seal() {
        this.isSealed = true;
    }
}
