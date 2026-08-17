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
                    "actionType", 30
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
                "x".repeat(200), "x".repeat(2000), "x".repeat(2000), "PRIVATE", "OTHER"
        );
        ItemUpdateRequest oversizedItem = new ItemUpdateRequest(
                "x".repeat(101), "x".repeat(101), "x".repeat(2001),
                "x".repeat(201), "x".repeat(2001), "x".repeat(2001), "PRIVATE", "OTHER"
        );

        assertThat(validator.validate(validItem)).isEmpty();
        assertThat(validator.validate(oversizedItem)).hasSize(6);
        assertThat(validator.validate(new ObjectionRequest("x".repeat(1000)))).isEmpty();
        assertThat(validator.validate(new ObjectionRequest("x".repeat(1001)))).hasSize(1);
    }

    @Test
    void multipartLimitsAreTwentyFiveAndThirtyMegabytes() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("25MB");
        assertThat(properties.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("30MB");
        assertThat(properties.getProperty("server.tomcat.max-swallow-size")).isEqualTo("35MB");
    }

    @Test
    void evidenceServiceFileLimitMatchesTheTwentyFiveMegabyteServletLimit() throws Exception {
        var maxFileSize = EvidenceSubmitService.class.getDeclaredField("MAX_FILE_SIZE");
        maxFileSize.setAccessible(true);

        assertThat(maxFileSize.getLong(null)).isEqualTo(25L * 1024 * 1024);
    }

    @Test
    void evidenceFilenameMatchesTheDatabaseColumnLimit() {
        EvidenceSubmitService service = new EvidenceSubmitService(null, null, null, null, null, null, null);
        MockMultipartFile valid = new MockMultipartFile(
                "file", "x".repeat(255), "application/pdf", new byte[]{1});
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "x".repeat(256), "application/pdf", new byte[]{1});

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateFile", valid))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateFile", oversized))
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
