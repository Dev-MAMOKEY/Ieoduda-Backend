package com.mamoki.ieojuda.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("이어주다 API")
                        .description("사망이 안전하게 확인되면 가족·동료에게 필요한 일을 정해진 순서로 이메일로 전달하는 사후 인계 서비스")
                        .version("v0.0.1"));
    }
}
