package com.mamoki.ieojuda.stage.entity;

import com.mamoki.ieojuda.plan.entity.Item;
import com.mamoki.ieojuda.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "item_dependencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dependency_id")
    private Long dependencyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** 이 item이 실행되기 전에 먼저 완료되어야 하는 선행 item */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_item_id", nullable = false)
    private Item dependsOnItem;

    @Builder
    public Dependency(Plan plan, Item item, Item dependsOnItem) {
        this.plan = plan;
        this.item = item;
        this.dependsOnItem = dependsOnItem;
    }
}
