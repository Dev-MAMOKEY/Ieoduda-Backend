package com.mamoki.ieojuda.global.openai.component;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.dto.OpenAIMessageDto;
import com.mamoki.ieojuda.global.openai.dto.OpenAIRequest;
import com.mamoki.ieojuda.global.openai.dto.OpenAIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #52 완료 조건 - "OpenAI 응답이 2,000토큰으로 제한된다" / "외부 API 무응답 시 설정된 제한 시간 안에
// 실패 처리된다"를 검증한다. 실제 타임아웃 대기(OpenAIConfig의 5초/15초)는 여기서 재현하지 않고,
// RestTemplate이 타임아웃 시 던지는 예외(ResourceAccessException)를 흉내내 변환 로직만 확인한다.
class OpenAIClientTest {

    private RestTemplate restTemplate;
    private OpenAIClient openAIClient;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        openAIClient = new OpenAIClient(restTemplate);
        ReflectionTestUtils.setField(openAIClient, "apiUrl", "http://openai.test/chat");
        ReflectionTestUtils.setField(openAIClient, "model", "test-model");
    }

    @Test
    void getChatCompletion_sendsRequestWithTwoThousandMaxTokens() {
        OpenAIResponse response = new OpenAIResponse(
                List.of(new OpenAIResponse.Choice(new OpenAIMessageDto("assistant", "{\"type\":\"QUESTION\"}"))));
        when(restTemplate.postForEntity(eq("http://openai.test/chat"), any(OpenAIRequest.class), eq(OpenAIResponse.class)))
                .thenAnswer(invocation -> {
                    OpenAIRequest request = invocation.getArgument(1);
                    assertThat(request.maxTokens()).isEqualTo(2000);
                    return ResponseEntity.ok(response);
                });

        OpenAIResponse result = openAIClient.getChatCompletion(List.of(new OpenAIMessageDto("user", "안녕")));

        assertThat(result).isEqualTo(response);
    }

    // issue #52 완료 조건 - "외부 API 무응답 시 설정된 제한 시간 안에 실패 처리된다"
    @Test
    void getChatCompletion_whenRestTemplateTimesOut_throwsAiServiceUnavailable() {
        when(restTemplate.postForEntity(any(String.class), any(OpenAIRequest.class), eq(OpenAIResponse.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> openAIClient.getChatCompletion(List.of(new OpenAIMessageDto("user", "안녕"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
    }

    @Test
    void getChatCompletion_whenResponseIsNotSuccessful_throwsAiServiceUnavailable() {
        when(restTemplate.postForEntity(any(String.class), any(OpenAIRequest.class), eq(OpenAIResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());

        assertThatThrownBy(() -> openAIClient.getChatCompletion(List.of(new OpenAIMessageDto("user", "안녕"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
    }

    @Test
    void getChatCompletion_whenResponseBodyIsNull_throwsAiServiceUnavailable() {
        when(restTemplate.postForEntity(any(String.class), any(OpenAIRequest.class), eq(OpenAIResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThatThrownBy(() -> openAIClient.getChatCompletion(List.of(new OpenAIMessageDto("user", "안녕"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
    }
}
