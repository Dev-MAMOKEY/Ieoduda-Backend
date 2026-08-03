package com.mamoki.ieojuda.global.openai.dto;

import java.util.List;

public record OpenAIResponse(List<Choice> choices) {
    public record Choice(OpenAIMessageDto message) {
    }
}
