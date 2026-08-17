package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.config.S3Properties;
import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.function.Supplier;

// issue #51 - store/load/delete 각각 짧은 재시도+회로차단을 별도로 둔다. store는 사용자 대면 업로드
// 경로라 더 빨리 실패해야 하고, delete/load는 백그라운드 스케줄러가 다음 주기에 다시 시도해주므로
// 상대적으로 여유를 둘 수 있다 - application.properties의 s3Put/s3Get/s3Delete 인스턴스 설정 참고.
@Slf4j
@Component
public class S3EvidenceStorageClient implements EvidenceStorageClient {

    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final Retry putRetry;
    private final CircuitBreaker putCircuitBreaker;
    private final Retry getRetry;
    private final CircuitBreaker getCircuitBreaker;
    private final Retry deleteRetry;
    private final CircuitBreaker deleteCircuitBreaker;

    public S3EvidenceStorageClient(S3Client s3Client, S3Properties s3Properties,
                                    RetryRegistry retryRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
        this.putRetry = retryRegistry.retry("s3Put");
        this.putCircuitBreaker = circuitBreakerRegistry.circuitBreaker("s3Put");
        this.getRetry = retryRegistry.retry("s3Get");
        this.getCircuitBreaker = circuitBreakerRegistry.circuitBreaker("s3Get");
        this.deleteRetry = retryRegistry.retry("s3Delete");
        this.deleteCircuitBreaker = circuitBreakerRegistry.circuitBreaker("s3Delete");
    }

    @Override
    public StoredEvidence store(Long caseId, EvidenceUpload upload) {
        String storageKey = StorageKeyGenerator.generate(caseId, upload.mimeType());

        try {
            // Supplier로 감싼 스트림을 넘긴다 - 전송 실패로 SDK가 재시도할 때마다
            // ContentStreamProvider가 새 스트림을 열어준다(멀티파트 임시 파일에서 다시 읽음).
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storageKey)
                    .contentType(upload.mimeType())
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();
            RequestBody requestBody = RequestBody.fromContentProvider(
                    ContentStreamProvider.fromInputStreamSupplier(upload.content()),
                    upload.contentLength(),
                    upload.mimeType());

            Supplier<Void> putOnce = Retry.decorateSupplier(putRetry, () -> {
                s3Client.putObject(request, requestBody);
                return null;
            });
            CircuitBreaker.decorateSupplier(putCircuitBreaker, putOnce).get();
        } catch (SdkException | CallNotPermittedException e) {
            log.error("[Evidence Store Failed] caseId={}, cause={}", caseId, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }

        return new StoredEvidence(storageKey, upload.contentLength());
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            Supplier<byte[]> loadOnce = Retry.decorateSupplier(getRetry, () -> s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(storageKey)
                            .build(),
                    ResponseTransformer.toBytes()).asByteArray());
            return CircuitBreaker.decorateSupplier(getCircuitBreaker, loadOnce).get();
        } catch (NoSuchKeyException e) { //S3에 해당 storageKey에 해당하는 파일이 없을 경우
            throw new CustomException(ErrorCode.EVIDENCE_NOT_FOUND);
        } catch (SdkException | CallNotPermittedException e) { // 네트워크 단계나 S3 연동 문제
            log.error("[Evidence Load Failed] storageKey={}, cause={}", storageKey, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Supplier<Void> deleteOnce = Retry.decorateSupplier(deleteRetry, () -> {
                s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(s3Properties.getBucket())
                                .key(storageKey)
                                .build());
                return null;
            });
            CircuitBreaker.decorateSupplier(deleteCircuitBreaker, deleteOnce).get();
        } catch (SdkException | CallNotPermittedException e) {
            log.error("[Evidence Delete Failed] storageKey={}, cause={}", storageKey, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }
    }
}
