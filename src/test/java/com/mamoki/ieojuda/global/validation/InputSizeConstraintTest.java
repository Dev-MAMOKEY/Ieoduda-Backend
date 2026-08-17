package com.mamoki.ieojuda.global.validation;

import com.mamoki.ieojuda.domain.account.dto.LoginRequest;
import com.mamoki.ieojuda.domain.account.dto.RefreshRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupRequest;
import com.mamoki.ieojuda.domain.account.dto.UserUpdateRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerUpdateRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceSubmitService;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.plan.dto.ItemUpdateRequest;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailRequest;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.recipient.dto.BackupRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientUpdateRequest;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionRequest;
import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.exception.GlobalExceptionHandler;
import jakarta.persistence.Column;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.io.InputStream;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputSizeConstraintTest {

    private static final Map<Class<?>, Map<String, Integer>> EXPECTED_MAX_LENGTHS = Map.ofEntries(
            Map.entry(LoginRequest.class, Map.of("email", 255, "password", 128)),
            // issue #56 - Refresh Token에 jti/iss/aud 클레임이 추가되면서 기존 255자로는 부족해져 2000자로 늘림
            Map.entry(RefreshRequest.class, Map.of("refreshToken", 2000)),
            // password는 100자(유출 비밀번호 검사 도입 시 passwordConfirm과 별개로 조정됨) - 실제 선언과 맞춤
            Map.entry(SignupRequest.class, Map.of("email", 255, "password", 100, "passwordConfirm", 128, "name", 100)),
            Map.entry(UserUpdateRequest.class, Map.of("email", 255, "name", 100)),
            Map.entry(ConfirmerRegisterRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(ConfirmerUpdateRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(DisputeContactRegisterRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(ConfirmerDecisionRequest.class, Map.of("inquiry", 1000)),
            Map.entry(PartnerReviewDecisionRequest.class, Map.of("failureReason", 1000)),
            Map.entry(ItemUpdateRequest.class, Map.of(
                    "targetName", 100,
                    "locationType", 100,
                    "action", 2000,
                    "title", 200,
                    "content", 2000,
                    "precondition", 2000,
                    "disclosureScope", 30,
                    "actionType", 30,
                    "semanticType", 30
            )),
            Map.entry(SelfWarningEmailRequest.class, Map.of("email", 255)),
            Map.entry(BackupRegisterRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(RecipientRegisterRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(RecipientUpdateRequest.class, Map.of("name", 100, "email", 255)),
            Map.entry(RecipientInviteDecisionRequest.class, Map.of("inquiry", 1000)),
            Map.entry(ObjectionRequest.class, Map.of("reason", 1000))
    );

    private static final Map<Class<?>, Map<String, Integer>> EXPECTED_COLUMN_LENGTHS = Map.of(
            Item.class, Map.of(
                    "targetName", 100, "locationType", 100, "action", 2000, "title", 200,
                    "content", 2000, "precondition", 2000, "sourceExcerpt", 2000
            ),
            Confirmer.class, Map.of("inquiry", 1000),
            Recipient.class, Map.of("inquiry", 1000),
            Objection.class, Map.of("reason", 1000),
            Evidence.class, Map.of("failureReason", 1000),
            com.mamoki.ieojuda.domain.account.entity.User.class, Map.of("name", 100)
    );

    @Test
    void requestDtoStringLimitsMatchTheInputContract() {
        EXPECTED_MAX_LENGTHS.forEach((type, fields) -> fields.forEach((field, expectedMax) -> {
            Size size;
            try {
                size = type.getDeclaredField(field).getAnnotation(Size.class);
            } catch (NoSuchFieldException exception) {
                throw new AssertionError(type.getSimpleName() + "." + field + " not found", exception);
            }

            assertThat(size)
                    .as("%s.%s must declare @Size", type.getSimpleName(), field)
                    .isNotNull();
            assertThat(size.max())
                    .as("%s.%s max length", type.getSimpleName(), field)
                    .isEqualTo(expectedMax);
        }));
    }

    @Test
    void databaseColumnsMatchTheDtoLimits() {
        EXPECTED_COLUMN_LENGTHS.forEach((type, fields) -> fields.forEach((field, expectedLength) -> {
            Column column;
            try {
                column = type.getDeclaredField(field).getAnnotation(Column.class);
            } catch (NoSuchFieldException exception) {
                throw new AssertionError(type.getSimpleName() + "." + field + " not found", exception);
            }
            assertThat(column.length())
                    .as("%s.%s database length", type.getSimpleName(), field)
                    .isEqualTo(expectedLength);
        }));
    }

    @Test
    void itemAndObjectionBoundaryValuesAreAcceptedAndOverflowValuesAreRejected() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ItemUpdateRequest validItem = new ItemUpdateRequest(
                "x".repeat(100), "x".repeat(100), "x".repeat(2000),
                "x".repeat(200), "x".repeat(2000), "x".repeat(2000), "PRIVATE", "OTHER", "x".repeat(30)
        );
        ItemUpdateRequest oversizedItem = new ItemUpdateRequest(
                "x".repeat(101), "x".repeat(101), "x".repeat(2001),
                "x".repeat(201), "x".repeat(2001), "x".repeat(2001), "PRIVATE", "OTHER", "x".repeat(31)
        );

        assertThat(validator.validate(validItem)).isEmpty();
        assertThat(validator.validate(oversizedItem)).hasSize(7);
        assertThat(validator.validate(new ObjectionRequest("token", "x".repeat(1000)))).isEmpty();
        assertThat(validator.validate(new ObjectionRequest("token", "x".repeat(1001)))).hasSize(1);
    }

    // issue #93 - maxWaitHours가 168/336/504 고정값이 아니라 168~720시간(7~30일) 범위를 자유롭게 받아야 한다
    @Test
    void recipientRegisterRequestMaxWaitHoursAcceptsFullSevenToThirtyDayRangeAndRejectsOutOfRange() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        UUID itemId = UUID.randomUUID();

        RecipientRegisterRequest lowerBound = new RecipientRegisterRequest(itemId, "이지수", "test@test.com", 168, null);
        RecipientRegisterRequest midRange = new RecipientRegisterRequest(itemId, "이지수", "test@test.com", 240, null);
        RecipientRegisterRequest upperBound = new RecipientRegisterRequest(itemId, "이지수", "test@test.com", 720, null);
        RecipientRegisterRequest belowRange = new RecipientRegisterRequest(itemId, "이지수", "test@test.com", 167, null);
        RecipientRegisterRequest aboveRange = new RecipientRegisterRequest(itemId, "이지수", "test@test.com", 721, null);

        assertThat(validator.validate(lowerBound)).isEmpty();
        assertThat(validator.validate(midRange)).isEmpty();
        assertThat(validator.validate(upperBound)).isEmpty();
        assertThat(validator.validate(belowRange)).hasSize(1);
        assertThat(validator.validate(aboveRange)).hasSize(1);
    }

    // issue #93 - 피그마 회원가입 화면은 이메일/비밀번호/비밀번호 확인 3개만 입력받으므로 이름 없이도 가입이 성공해야 한다
    @Test
    void signupRequestAcceptsMissingName() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        SignupRequest withoutName = new SignupRequest("size-limit-test@example.com", "password1234", "password1234", null);

        assertThat(validator.validate(withoutName)).isEmpty();
    }

    // issue #54 이후 이 세 값은 하드코딩이 아니라 환경변수(MULTIPART_MAX_FILE_SIZE 등)로 외부화됐다.
    // application.properties에는 "${MULTIPART_MAX_FILE_SIZE}" 같은 미해석 플레이스홀더만 있어 그 파일을
    // 그대로 읽으면 항상 실패한다 - 대신 (1) application.properties가 올바른 환경변수 이름을 가리키는지,
    // (2) 커밋된 기본값 문서인 .env.example이 여전히 50MB/55MB/60MB 계약을 명시하는지 두 가지로 검증한다.
    @Test
    void multipartLimitsAreFiftyAndFiftyFiveMegabytes() throws IOException {
        Properties appProperties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            appProperties.load(input);
        }

        assertThat(appProperties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("${MULTIPART_MAX_FILE_SIZE}");
        assertThat(appProperties.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("${MULTIPART_MAX_REQUEST_SIZE}");
        assertThat(appProperties.getProperty("server.tomcat.max-swallow-size")).isEqualTo("${TOMCAT_MAX_SWALLOW_SIZE}");

        Properties envExample = new Properties();
        try (InputStream input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(".env.example"))) {
            envExample.load(input);
        }

        assertThat(envExample.getProperty("MULTIPART_MAX_FILE_SIZE")).isEqualTo("50MB");
        assertThat(envExample.getProperty("MULTIPART_MAX_REQUEST_SIZE")).isEqualTo("55MB");
        assertThat(envExample.getProperty("TOMCAT_MAX_SWALLOW_SIZE")).isEqualTo("60MB");
    }

    @Test
    void evidenceServiceFileLimitMatchesTheFiftyMegabyteServletLimit() throws Exception {
        var maxFileSize = EvidenceSubmitService.class.getDeclaredField("MAX_FILE_SIZE");
        maxFileSize.setAccessible(true);

        assertThat(maxFileSize.getLong(null)).isEqualTo(50L * 1024 * 1024);
    }

    @Test
    void evidenceFilenameMatchesTheDatabaseColumnLimit() {
        EvidenceSubmitService service = new EvidenceSubmitService(null, null, null, null, null, null, null, null);
        MockMultipartFile valid = new MockMultipartFile(
                "file", "x".repeat(255), "application/pdf", new byte[]{1});
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "x".repeat(256), "application/pdf", new byte[]{1});

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateBasics", valid))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateBasics", oversized))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EVIDENCE_SUBMISSION_INVALID));
    }

    @Test
    void maxUploadSizeExceptionHasDedicatedHandler() {
        var response = new GlobalExceptionHandler()
                .handle(new MaxUploadSizeExceededException(25L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

}
