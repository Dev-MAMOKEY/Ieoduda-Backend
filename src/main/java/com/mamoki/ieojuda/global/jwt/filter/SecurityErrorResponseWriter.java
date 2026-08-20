package com.mamoki.ieojuda.global.jwt.filter;

import com.mamoki.ieojuda.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.LocalDateTime;

// JwtAuthenticationFilter와 SecurityConfig의 AuthenticationEntryPoint가 공유하는 401 응답 작성 유틸.
// 둘 다 DispatcherServlet보다 앞단(필터 체인)에서 동작해서 GlobalExceptionHandler(@RestControllerAdvice)를
// 타지 않기 때문에, 같은 RsData 형태로 직접 응답을 만들어야 다른 API들과 에러 응답 포맷이 일관된다.
public class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = """
                {"success":false,"data":null,"error":{"code":"%s","message":"%s"},"timestamp":"%s"}""".formatted(
                errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now());
        response.getWriter().write(body);
    }
}
