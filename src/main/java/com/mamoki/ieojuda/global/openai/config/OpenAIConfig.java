package com.mamoki.ieojuda.global.openai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class OpenAIConfig {

    // issue #52 - 이 RestTemplate은 OpenAI 호출 전용이라, 여기서 건 타임아웃이 다른 외부 연동에 영향을 주지 않는다.
    // 연결 5초·응답 15초를 넘기면 RestClientException으로 실패해 OpenAIClient가 AI_SERVICE_UNAVAILABLE로 변환한다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Value("${openai.api-key}")
    private String apiKey;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().set("Authorization", "Bearer " + apiKey);
                    return execution.execute(request, body);
                })
                .build();
    }
}
