package com.mamoki.ieojuda.global.email.template;

import com.mamoki.ieojuda.global.email.contract.EmailContent;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EmailBuilder {

    private static final String SERVICE_NAME = "이어두다";
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm", Locale.KOREAN);


    private EmailBuilder() {
        //인스턴스화 방지
    }

    /**
     * 링크 기반 공통 이메일 본문 조립.
     * @param roleName     역할명
     * @param nextAction   필요한 다음 행동
     * @param expiresAt    링크 만료 시각
     * @param secureLink   보안 링크
     * @param contactEmail 문의 주소
     */
    public static EmailContent build(
            String roleName,
            String nextAction,
            ZonedDateTime expiresAt,
            String secureLink,
            String contactEmail
    ) {
        String subject = String.format("[%s] %s 안내", SERVICE_NAME, roleName);

        String body = String.format("""
                %s입니다.

                [%s] %s

                다음 행동: %s

                보안 링크: %s
                (이 링크는 %s까지 유효합니다)

                본 메일은 발신 전용이며, 링크를 통해서만 안전하게 진행됩니다.
                의심스러운 요청이나 피싱이 우려되면 아래 문의 주소로 연락해주세요.

                문의: %s
                """,
                SERVICE_NAME,
                SERVICE_NAME, roleName,
                nextAction,
                secureLink,
                expiresAt.format(EXPIRY_FORMAT),
                contactEmail
        );

        return new EmailContent(subject, body);
    }

    /**
     * OTP 코드 이메일 본문 조립
     * @param otpCode      6자리 등 OTP 코드 값
     * @param expiresAt    코드 만료 시각
     * @param contactEmail 문의 주소
     * 검증을 통과한 EmailContent
     */
    public static EmailContent buildOtp(
            String otpCode,
            ZonedDateTime expiresAt,
            String contactEmail
    ) {
        String subject = String.format("[%s] 인증 코드 안내", SERVICE_NAME);

        String body = String.format("""
                %s입니다.

                인증 코드: %s

                다음 행동: 화면에 위 코드를 입력해 본인 확인을 완료해주세요.

                (이 코드는 %s까지 유효합니다)

                본 메일은 발신 전용입니다. 이 코드를 타인에게 절대 알려주지 마세요.
                의심스러운 요청이나 피싱이 우려되면 아래 문의 주소로 연락해주세요.

                문의: %s
                """,
                SERVICE_NAME,
                otpCode,
                expiresAt.format(EXPIRY_FORMAT),
                contactEmail
        );


        return new EmailContent(subject, body);
    }



}
