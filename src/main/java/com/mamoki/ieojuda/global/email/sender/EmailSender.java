package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;

public interface EmailSender {

   /** 이메일 발송 인터페이스
   * @param  toEmail -> 수신자 이메일 주소
   * @param  content > 발송할 본문
   */
    EmailSendResult send(String toEmail, EmailContent content);
}
