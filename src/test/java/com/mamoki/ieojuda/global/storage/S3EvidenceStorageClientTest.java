package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.config.S3Properties;
import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #51 - Resilience4j 재시도/회로차단이 S3EvidenceStorageClient의 기존 예외 계약
// (SdkException/NoSuchKeyException -> CustomException(EVIDENCE_STORAGE_FAILED/EVIDENCE_NOT_FOUND))을
// 유지하면서 짧은 재시도로 일시 장애를 흡수하는지 검증한다.
class S3EvidenceStorageClientTest {

    private S3Client s3Client;
    private S3EvidenceStorageClient storageClient;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Properties s3Properties = mock(S3Properties.class);
        when(s3Properties.getBucket()).thenReturn("test-bucket");

        RetryConfig retryConfig = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build();
        RetryRegistry retryRegistry = RetryRegistry.of(Map.of(
                "s3Put", retryConfig, "s3Get", retryConfig, "s3Delete", retryConfig));
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(Map.of(
                "s3Put", CircuitBreakerConfig.ofDefaults(),
                "s3Get", CircuitBreakerConfig.ofDefaults(),
                "s3Delete", CircuitBreakerConfig.ofDefaults()));
        storageClient = new S3EvidenceStorageClient(s3Client, s3Properties, retryRegistry, circuitBreakerRegistry);
    }

    @Test
    void store_whenFirstAttemptsFailThenSucceed_retriesAndSucceeds() {
        doThrow(SdkException.builder().message("boom").build())
                .doThrow(SdkException.builder().message("boom").build())
                .doReturn(PutObjectResponse.builder().build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

        EvidenceUpload upload = new EvidenceUpload("proof.pdf", "application/pdf",
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);

        var stored = storageClient.store(1L, upload);

        assertThat(stored.fileSize()).isEqualTo(3);
        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void store_whenAllAttemptsFail_throwsEvidenceStorageFailed() {
        doThrow(SdkException.builder().message("boom").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        EvidenceUpload upload = new EvidenceUpload("proof.pdf", "application/pdf",
                () -> new ByteArrayInputStream(new byte[]{1}), 1);

        assertThatThrownBy(() -> storageClient.store(1L, upload))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_STORAGE_FAILED));
        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void delete_whenAllAttemptsFail_throwsEvidenceStorageFailed() {
        doThrow(SdkException.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> storageClient.delete("evidence/1/uuid.pdf"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_STORAGE_FAILED));
        verify(s3Client, times(3)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_whenSucceedsOnFirstTry_deletesExactlyOnce() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        storageClient.delete("evidence/1/uuid.pdf");

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void load_whenKeyDoesNotExist_throwsEvidenceNotFound() {
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThatThrownBy(() -> storageClient.load("evidence/missing.pdf"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
    }
}
