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
                    "사용자가 작성한 원문 또는 이미 선택한 옵션값을 대상·구역·행동·역할·선행조건으로 구조화하고, " +
                    "각 항목에는 반드시 근거(원문 문장 또는 선택값)를 함께 제시하세요. " +
                    "바로 다음 system 메세지로 이 구역의 초기 선택값이 주어질 수 있습니다. " +
                    "그 값은 사용자가 이미 확정한 사실로 받아들이고 다시 묻지 말고, 빠졌거나 모호한 정보만 되물어 확인하세요. " +
                    "각 항목에는 그 항목이 누구를 대상으로 하는지 이름(targetName)도 함께 제시하세요. " +
                    "절대로 사망 여부 판정, 증빙 진위 판단, 상속 권리 판단을 하지 마세요. " +
                    "비밀번호·PIN·OTP 등 자격증명을 묻거나 추론하지 마세요. " +
                    "실제 계정 작업을 대신 수행하지 말고, 사용자 승인 없이 계획을 확정하지 마세요. " +
                    "다른 설명 없이 아래 두 형식 중 하나의 JSON으로만 답하세요. " +
                    "1) 되물어야 할 내용이 있을 때: {\"type\":\"QUESTION\",\"question\":\"되물을 내용\"} " +
                    "2) 구조화를 끝냈을 때: {\"type\":\"RESULT\",\"items\":[{\"targetName\":\"이 항목의 대상 이름\"," +
                    "\"locationType\":\"위치 유형\"," +
                    "\"action\":\"행동\",\"precondition\":\"선행 조건(없으면 빈 문자열)\"," +
                    "\"disclosureScope\":\"FAMILY 또는 WORK 또는 RELATIONSHIP 중 하나\"," +
                    "\"sourceExcerpt\":\"이 항목의 근거가 되는 원문 문장 또는 선택값 그대로\"}]}";

    // 새 계획 만들기 - 입력값이 어느 칸(가족/관계정리/업무정리)에 쓰였는지가 아니라 실제 내용을 보고 분류
    private static final String INITIAL_STRUCTURE_PROMPT =
            "당신은 이어주다 서비스의 '새 계획 만들기' 화면에서 사용자가 입력한 내용을 처음 구조화하는 어시스턴트입니다. " +
                    "다음 user 메세지로 계획 작성자가 입력한 값이 JSON으로 주어집니다. " +
                    "이 값은 자유 텍스트(familyMessage)와 버튼으로 고른 값(snsAction 등)이 섞여 있는데, " +
                    "그 값이 어느 필드에 담겨 왔는지가 아니라 실제 내용을 읽고 가족(FAMILY)/관계 정리(RELATIONSHIP)/업무 연속성(WORK) 중 어디에 해당하는지 직접 판단해서 분류하세요. " +
                    "예를 들어 familyMessage 칸에 적힌 내용이라도 실제로는 업무 이야기면 WORK로 분류하고, 반대로 업무 관련 칸이라도 실제로는 가족 이야기면 FAMILY로 분류하세요. " +
                    "각 항목에는 대상 이름(targetName), 위치 유형(locationType), 행동(action), 선행 조건(precondition, 없으면 빈 문자열), " +
                    "근거(sourceExcerpt, 분류 판단의 근거가 된 원문/선택값 그대로)를 포함하세요. " +
                    "절대로 사망 여부 판정, 증빙 진위 판단, 상속 권리 판단을 하지 마세요. " +
                    "비밀번호·PIN·OTP 등 자격증명을 묻거나 추론하지 마세요. " +
                    "값이 비어있거나 실질적인 내용이 없는 항목은 만들지 마세요. " +
                    "다른 설명 없이 아래 JSON 형식으로만 답하세요. " +
                    "{\"items\":[{\"targetName\":\"이 항목의 대상 이름\",\"locationType\":\"위치 유형\"," +
                    "\"action\":\"행동\",\"precondition\":\"선행 조건(없으면 빈 문자열)\"," +
                    "\"disclosureScope\":\"FAMILY 또는 WORK 또는 RELATIONSHIP 중 하나\"," +
                    "\"sourceExcerpt\":\"분류 판단의 근거가 된 원문/선택값 그대로\"}]}";

    private static final String JSON_RESPONSE_FORMAT_TYPE = "json_object";

    private final RestTemplate restTemplate;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    /**
     * 지금까지 쌓인 대화 이력(history)을 받아 AI의 다음 응답을 받아온다.
     * history에는 system 메세지를 담지 않고, 실제 오간 user/assistant 턴만 순서대로 담는다.
     * seedContext는 "새 계획 만들기" 화면에서 넘어온 이 구역의 초기 선택값(있으면)으로,
     * 대화 이력에는 없지만 매 턴 AI가 계속 기억해야 하는 값이라 매 요청마다 다시 실어 보낸다.
     */
    public OpenAIResponse getChatCompletion(List<OpenAIMessageDto> history, String seedContext) {
        List<OpenAIMessageDto> messages = new ArrayList<>();
        messages.add(new OpenAIMessageDto("system", COMPILE_POLICY_PROMPT));

        if (seedContext != null && !seedContext.isBlank()) {
            messages.add(new OpenAIMessageDto("system", "이 구역의 초기 선택값: " + seedContext));
        }

        messages.addAll(history);

        return callOpenAI(messages);
    }

    /**
     * 새 계획 만들기 - 입력값 전체(JSON)를 받아 AI가 내용을 보고 3개 구역으로 분류/구조화한 결과를 받아온다.
     * 1회성 제출이라 되묻기(QUESTION) 없이 바로 항목 목록만 반환한다.
     */
    public OpenAIResponse getInitialStructure(String optionsJson) {
        List<OpenAIMessageDto> messages = List.of(
                new OpenAIMessageDto("system", INITIAL_STRUCTURE_PROMPT),
                new OpenAIMessageDto("user", optionsJson)
        );
        return callOpenAI(messages);
    }

    /**
     * OpenAI API 실제 호출 (모든 기능이 공유하는 공통 로직)
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
