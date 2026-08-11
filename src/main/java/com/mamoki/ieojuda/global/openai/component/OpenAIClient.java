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

    // 대화창(첫 메세지부터 끝까지 하나의 채팅) 전체에서 항상 지켜야 하는 고정 정책.
    // "계획 만들기" 같은 별도 입력 폼이 없어졌으므로, 첫 턴부터 자유 텍스트만 보고
    // 여러 사람·여러 주제가 섞인 한 발화를 대상별로 쪼개 항목화하는 것까지 이 프롬프트가 책임진다.
    // 응답 형식(JSON 스키마)까지 강제해서, 서비스 코드가 "되묻는 중"인지 "구조화 완료"인지 파싱으로 구분할 수 있게 함
    private static final String COMPILE_POLICY_PROMPT =
            "당신은 이어주다 서비스의 AI 어시스턴트입니다. " +
                    "사용자가 자유롭게 이야기하는 내용을 듣고, 그 안에서 유고 시 처리해야 할 일들을 찾아 " +
                    "대상·행동·역할·선행조건으로 구조화하세요. " +
                    "한 번의 발화에 여러 사람이나 여러 주제가 섞여 있으면(예: '민수한테 SNS 정리 부탁하고 아내한테는 클라우드 사진첩 위치를 알려주고 싶어요') " +
                    "절대로 한 항목으로 뭉치지 말고 각각을 별도 항목으로 나누세요. " +
                    "각 항목마다 가족(FAMILY)/관계 정리(RELATIONSHIP)/업무 연속성(WORK) 중 실제 내용에 맞는 곳으로 직접 판단해서 분류하세요. " +
                    "각 항목에는 반드시 근거(원문 문장)를 함께 제시하세요. " +
                    "각 항목에는 그 항목이 누구를 대상으로 하는지 이름(targetName)도 함께 제시하세요. " +
                    "정보가 빠졌거나 모호한 부분이 있으면 항목을 만들지 말고 되물어 확인하세요. " +
                    "모든 항목에는 반드시 sortOrder(정수, 낮을수록 먼저 실행)를 매기세요. sortOrder는 null이면 안 됩니다. " +
                    "이번에 만드는 항목들 사이에 실행 순서를 지켜야 하는 관계가 있다면(예: 자료를 보존해야 하는 항목은 " +
                    "그 자료가 있는 계정을 삭제하는 항목보다 먼저 실행되어야 함, 거래처 통지는 채널 폐쇄보다 먼저, " +
                    "기록 반출은 기기 초기화보다 먼저) 그 순서에 맞게 서로 다른 sortOrder 값을 매기세요. " +
                    "순서를 지킬 필요가 없는 항목들끼리는 모두 0을 매기세요. " +
                    "절대로 사망 여부 판정, 증빙 진위 판단, 상속 권리 판단을 하지 마세요. " +
                    "비밀번호·PIN·OTP 등 자격증명을 묻거나 추론하지 마세요. " +
                    "실제 계정 작업을 대신 수행하지 말고, 사용자 승인 없이 계획을 확정하지 마세요. " +
                    "다른 설명 없이 아래 두 형식 중 하나의 JSON으로만 답하세요. " +
                    "1) 되물어야 할 내용이 있을 때: {\"type\":\"QUESTION\",\"question\":\"되물을 내용\"} " +
                    "2) 구조화를 끝냈을 때: {\"type\":\"RESULT\",\"items\":[{\"targetName\":\"이 항목의 대상 이름\"," +
                    "\"locationType\":\"위치 유형\"," +
                    "\"action\":\"행동\",\"precondition\":\"선행 조건(없으면 빈 문자열)\"," +
                    "\"disclosureScope\":\"FAMILY 또는 WORK 또는 RELATIONSHIP 중 하나\"," +
                    "\"sourceExcerpt\":\"이 항목의 근거가 되는 원문 문장\"," +
                    "\"sortOrder\":실행 순서를 나타내는 정수(낮을수록 먼저)}]}";

    private static final String JSON_RESPONSE_FORMAT_TYPE = "json_object";

    private final RestTemplate restTemplate;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    /**
     * 지금까지 쌓인 대화 이력(history)을 받아 AI의 다음 응답을 받아온다.
     * history에는 system 메세지를 담지 않고, 실제 오간 user/assistant 턴만 순서대로 담는다.
     * 계획 생성 폼이 없어졌으므로 첫 턴부터 이 메서드 하나로 처리한다(별도의 1회성 구조화 API는 없음).
     */
    public OpenAIResponse getChatCompletion(List<OpenAIMessageDto> history) {
        List<OpenAIMessageDto> messages = new ArrayList<>();
        messages.add(new OpenAIMessageDto("system", COMPILE_POLICY_PROMPT));
        messages.addAll(history);

        return callOpenAI(messages);
    }

    /**
     * OpenAI API 실제 호출
     */
    private OpenAIResponse callOpenAI(List<OpenAIMessageDto> messages) {
        OpenAIRequest openAiRequest = new OpenAIRequest(model, messages, new OpenAIRequest.ResponseFormat(JSON_RESPONSE_FORMAT_TYPE));

        ResponseEntity<OpenAIResponse> chatResponse = restTemplate.postForEntity(
                apiUrl,
                openAiRequest,
                OpenAIResponse.class
        );

        if (!chatResponse.getStatusCode().is2xxSuccessful() || chatResponse.getBody() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return chatResponse.getBody();
    }
}
