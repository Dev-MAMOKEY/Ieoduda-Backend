package com.mamoki.ieojuda.global.openai.dto;

import java.util.List;

public record OpenAIRequest(String model, List<OpenAIMessageDto> messages) {
}
