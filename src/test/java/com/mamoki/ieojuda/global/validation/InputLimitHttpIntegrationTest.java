package com.mamoki.ieojuda.global.validation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InputLimitHttpIntegrationTest {

    private static final long MEBIBYTE = 1024L * 1024L;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void oversizedJsonFieldReturnsBadRequestWithStandardErrorCode() throws Exception {
        String json = """
                {
                  "email": "size-limit-test@example.com",
                  "password": "password1234",
                  "passwordConfirm": "password1234",
                  "name": "%s"
                }
                """.formatted("가".repeat(101));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/auth/signup"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
    }

    @Test
    void fileLargerThanTwentyFiveMegabytesReturnsPayloadTooLarge() throws Exception {
        HttpResponse<String> response = sendMultipart(25L * MEBIBYTE + 1, 0);

        assertPayloadTooLarge(response);
    }

    @Test
    void multipartRequestLargerThanThirtyMegabytesReturnsPayloadTooLarge() throws Exception {
        HttpResponse<String> response = sendMultipart(25L * MEBIBYTE, 5L * MEBIBYTE + 1);

        assertPayloadTooLarge(response);
    }

    private HttpResponse<String> sendMultipart(long fileBytes, long paddingBytes) throws Exception {
        String boundary = "ieoduda-size-boundary";
        byte[] fileHeader = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"proof.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] paddingHeader = ("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"padding\"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] closing = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpRequest.BodyPublisher body;
        long contentLength;
        if (paddingBytes > 0) {
            body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(fileHeader),
                    zeroBody(fileBytes),
                    HttpRequest.BodyPublishers.ofByteArray(paddingHeader),
                    zeroBody(paddingBytes),
                    HttpRequest.BodyPublishers.ofByteArray(closing)
            );
            contentLength = fileHeader.length + fileBytes + paddingHeader.length + paddingBytes + closing.length;
        } else {
            body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(fileHeader),
                    zeroBody(fileBytes),
                    HttpRequest.BodyPublishers.ofByteArray(closing)
            );
            contentLength = fileHeader.length + fileBytes + closing.length;
        }

        Flow.Publisher<? extends ByteBuffer> fixedLengthBody = body;
        HttpRequest request = HttpRequest.newBuilder(uri("/api/release-cases/" + java.util.UUID.randomUUID() + "/evidence/submit"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.fromPublisher(fixedLengthBody, contentLength))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.BodyPublisher zeroBody(long length) {
        return HttpRequest.BodyPublishers.ofInputStream(() -> new ZeroInputStream(length));
    }

    private void assertPayloadTooLarge(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static final class ZeroInputStream extends InputStream {
        private long remaining;

        private ZeroInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int count = (int) Math.min(length, remaining);
            java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
            remaining -= count;
            return count;
        }
    }
}
