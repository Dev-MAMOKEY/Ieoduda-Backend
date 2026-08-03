package com.mamoki.ieojuda.global.openai.dto;

import lombok.Getter;

import java.util.List;

public record OpenAIResponse(List<Choice> choices) {

    @Getter
    public static class Choice{
        private OpenAIMessageDto openAIMessageDto;
    }
}
