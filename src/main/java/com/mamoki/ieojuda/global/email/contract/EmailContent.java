package com.mamoki.ieojuda.global.email.contract;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;



public record EmailContent(
        String subject,
        String body
) {
    public EmailContent {
        if (subject == null || subject.isBlank()) {
            throw new CustomException(ErrorCode.EMAIL_CONTENT_GENERATION_FAILED);
        }
        if (body == null || body.isBlank()) {
            throw new CustomException(ErrorCode.EMAIL_CONTENT_GENERATION_FAILED);
        }
    }
}