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
}
