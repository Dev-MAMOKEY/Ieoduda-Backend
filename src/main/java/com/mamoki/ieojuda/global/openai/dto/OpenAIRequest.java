package com.mamoki.ieojuda.global.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAIRequest(
        String model,
        List<OpenAIMessageDto> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat
) {
    // OpenAI에게 응답을 항상 유효한 JSON으로만 달라고 강제하는 옵션 (예: new ResponseFormat("json_object"))
    public record ResponseFormat(String type) {
    }
}
