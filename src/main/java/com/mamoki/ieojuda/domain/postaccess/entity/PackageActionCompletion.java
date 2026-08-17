package com.mamoki.ieojuda.domain.postaccess.entity;

import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

// 명세서 "역할별 사후 패키지" - 행동(항목) 완료 기록. 조회는 봉인된 스냅샷(PlanSnapshotDto)만 읽으므로
// (issue #42 결정사항, 라이브 Item 테이블 불가) 완료 여부는 스냅샷과 별개인 이 엔티티가 담당한다.
// itemId는 스냅샷 기준 참조일 뿐 라이브 Item과의 FK가 아니다.
@Entity
@Table(name = "package_action_completions",
        uniqueConstraints = @UniqueConstraint(name = "UQ_package_action_completions_stage_item",
                columnNames = {"stage_id", "item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PackageActionCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "completion_id")
    private UUID completionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private HandoverStage handoverStage;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public PackageActionCompletion(HandoverStage handoverStage, UUID itemId) {
        this.handoverStage = handoverStage;
        this.itemId = itemId;
        this.completedAt = LocalDateTime.now();
    }
}
