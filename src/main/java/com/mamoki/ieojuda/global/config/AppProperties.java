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

    @Value("${app.posthumous-link-ttl-hours}")
    private long posthumousLinkTtlHours;

    @Value("${app.otp-ttl-minutes}")
    private long otpTtlMinutes;

    @Value("${app.otp-max-attempts}")
    private int otpMaxAttempts;

    // OTP 확인 후 "역할별 사후 패키지" 화면을 열어둘 수 있는 시간. 링크(used=true)와 달리 세션은 여러 번 조회해야 해서 별도 TTL을 둔다
    @Value("${app.posthumous-session-ttl-minutes}")
    private long posthumousSessionTtlMinutes;
}
