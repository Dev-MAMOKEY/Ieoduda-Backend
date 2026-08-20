package com.mamoki.ieojuda.global.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@NoArgsConstructor
@Component
public class AppProperties {

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.contact-email}")
    private String contactEmail;

    @Value("${app.invite-token-ttl-hours}")
    private long inviteTokenTtlHours;

    // issue #41 - 수락 완료 후 발급되는 사망신고/증빙제출 등 장기 보안 토큰의 수명(일)
    @Value("${app.active-token-ttl-days}")
    private long activeTokenTtlDays;

    // issue #51 - 이메일 아웃박스 워커가 이 횟수만큼 실패하면 DEAD로 전이하고 재시도를 멈춘다.
    @Value("${app.email.outbox.max-attempts}")
    private int emailOutboxMaxAttempts;
}
