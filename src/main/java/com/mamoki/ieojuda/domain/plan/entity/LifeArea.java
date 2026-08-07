package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.global.entity.BaseUpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "life_areas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifeArea extends BaseUpdatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "life_id")
    private Long lifeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private LifeAreaCategory category;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "ai_structured_result", columnDefinition = "TEXT")
    private String aiStructuredResult;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;


    // 명세서 삶의 구역 재작성에서 사용자가 처음 원문저장에서 AI 구조조화 결과, 검토 완료 시각 없어서 우선 제외 시킴
    @Builder
    public LifeArea(Plan plan, LifeAreaCategory category, String rawText) {
        this.plan = plan;
        this.category = category;
        this.rawText = rawText;
    }

    // AI 구조화 요청 시점에서 호출하기 위해 설정
    public void applyAiStructuredResult(String aiStructuredResult) {
        this.aiStructuredResult = aiStructuredResult;
    }

    // 새 계획 만들기 화면의 "세부사항 대화하기" 버튼 - 구역별 초기 입력(자유 텍스트 또는 선택값 JSON)을 저장
    // 대화 턴마다 이 값을 다시 함께 보내야 AI가 초기 선택값을 계속 기억함
    public void applyRawText(String rawText) {
        this.rawText = rawText;
    }

    // AI 구조화 검토에서 호출되게 설정
    public void review() {
        this.reviewedAt = LocalDateTime.now();
    }
}
