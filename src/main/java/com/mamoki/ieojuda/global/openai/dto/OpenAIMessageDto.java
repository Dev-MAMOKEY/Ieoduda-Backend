package com.mamoki.ieojuda.global.openai.dto;

public record OpenAIMessageDto(String role, String content) {
        //openai request객체 실제 서비스에서 role값은 필요없지만 openai응답형식으로 인해 role필드 추가
}
