package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.BounceType;
import com.mamoki.ieojuda.global.email.contract.EmailFaill;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

public class FailureAnalyzer {

    public record Result(BounceType bounceType, EmailFaill emailFaill) {
    }

    private FailureAnalyzer() {
    }

    public static Result analyze(Exception exception) {
        // 주소 형식 오류 (전송 시도 이전, MimeMessageHelper.setTo() 등에서 발생)
        if (exception instanceof AddressException) {
            return new Result(BounceType.PERMANENT, EmailFaill.INVALID_ADDRESS_FORMAT);
        }

        // 메일 서버 인증 실패
        if (exception instanceof MailAuthenticationException) {
            return new Result(BounceType.TEMPORARY, EmailFaill.AUTHENTICATION_FAILED);
        }

        // 메일 전송 실패
        if (exception instanceof MailSendException mailSendException) {
            if (hasSendFailedCause(mailSendException)) {
                return new Result(BounceType.PERMANENT, EmailFaill.INVALID_ADDRESS_FORMAT);
            }
            return new Result(BounceType.TEMPORARY, EmailFaill.CONNECTION_TIMEOUT);
        }

        // 알 수 없는 오류
        return new Result(BounceType.TEMPORARY, EmailFaill.UNKNOWN);
    }

    private static boolean hasSendFailedCause(MailSendException mailSendException) {
        for (Exception messageException : mailSendException.getMessageExceptions()) {
            if (messageException instanceof SendFailedException) {
                return true;
            }
        }
        return false;
    }
}
