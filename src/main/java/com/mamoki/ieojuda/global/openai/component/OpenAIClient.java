package com.mamoki.ieojuda.global.openai.component;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.dto.OpenAIMessageDto;
import com.mamoki.ieojuda.global.openai.dto.OpenAIRequest;
import com.mamoki.ieojuda.global.openai.dto.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAIClient {

    // 삶의 구역 작성 - AI 구조화 대화에서 항상 지켜야 하는 고정 정책 (대화 턴 수와 무관하게 매 요청 동일하게 포함)
    // 응답 형식(JSON 스키마)까지 강제해서, 서비스 코드가 "되묻는 중"인지 "구조화 완료"인지 파싱으로 구분할 수 있게 함
    private static final String COMPILE_POLICY_PROMPT =
            "당신은 이어주다 서비스의 '삶의 구역 작성' 단계를 돕는 어시스턴트입니다. " +
                    "사용자가 작성한 원문을 대상·구역·행동·역할·선행조건으로 구조화하고, " +
                    "각 항목에는 반드시 원문 근거를 함께 제시하세요. " +
                    "담당자가 누락되었거나 표현이 모호하면 사용자에게 되물어 확인하세요. " +
                    "절대로 사망 여부 판정, 증빙 진위 판단, 상속 권리 판단을 하지 마세요. " +
                    "비밀번호·PIN·OTP 등 자격증명을 묻거나 추론하지 마세요. " +
                    "실제 계정 작업을 대신 수행하지 말고, 사용자 승인 없이 계획을 확정하지 마세요. " +
                    "다른 설명 없이 아래 두 형식 중 하나의 JSON으로만 답하세요. " +
                    "1) 되물어야 할 내용이 있을 때: {\"type\":\"QUESTION\",\"question\":\"되물을 내용\"} " +
                    "2) 구조화를 끝냈을 때: {\"type\":\"RESULT\",\"items\":[{\"locationType\":\"위치 유형\"," +
                    "\"action\":\"행동\",\"precondition\":\"선행 조건(없으면 빈 문자열)\"," +
                    "\"disclosureScope\":\"FAMILY 또는 WORK 또는 RELATIONSHIP 중 하나\"," +
                    "\"sourceExcerpt\":\"이 항목의 근거가 되는 원문 문장 그대로\"}]}";

    private static final String JSON_RESPONSE_FORMAT_TYPE = "json_object";

    private final RestTemplate restTemplate;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    /**
     * 지금까지 쌓인 대화 이력(history)을 받아 AI의 다음 응답을 받아온다.
     * history에는 system 메세지를 담지 않고, 실제 오간 user/assistant 턴만 순서대로 담는다.
     */
    public OpenAIResponse getChatCompletion(List<OpenAIMessageDto> history) {
        // step 1. OpenAI 요청 구성
        OpenAIRequest openAiRequest = getOpenAIRequest(history);

        // step 2. RestTemplate을 통해 OpenAI API POST 요청 전송
        ResponseEntity<OpenAIResponse> chatResponse = restTemplate.postForEntity(
                apiUrl,
                openAiRequest,
                OpenAIResponse.class
        );

        // step 3. 응답 실패 처리
        if (!chatResponse.getStatusCode().is2xxSuccessful() || chatResponse.getBody() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // step 4. 성공 시 응답 본문 반환
        return chatResponse.getBody();
    }

    /**
     * OpenAI 요청 구성
     */
    private OpenAIRequest getOpenAIRequest(List<OpenAIMessageDto> history) {
        // step 1-1. system 메세지 작성 - 고정된 서비스 정책 (매 요청 동일)
        OpenAIMessageDto systemMessage = new OpenAIMessageDto("system", COMPILE_POLICY_PROMPT);

        // step 1-2. system 메세지 다음에 지금까지의 실제 대화 이력을 순서대로 이어붙임
        List<OpenAIMessageDto> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(history);

        // step 1-3. 모델 이름, 메세지, 응답 형식(JSON 강제)을 포함한 요청 객체 생성
        return new OpenAIRequest(model, messages, new OpenAIRequest.ResponseFormat(JSON_RESPONSE_FORMAT_TYPE));
    }
}
