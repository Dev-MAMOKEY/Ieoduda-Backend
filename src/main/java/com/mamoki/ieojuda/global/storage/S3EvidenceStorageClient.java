package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.config.S3Properties;
import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class S3EvidenceStorageClient implements EvidenceStorageClient {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public StoredEvidence store(Long caseId, EvidenceUpload upload) {
        String storageKey = StorageKeyGenerator.generate(caseId, upload.mimeType());

        try {
            // Supplier로 감싼 스트림을 넘긴다 - 전송 실패로 SDK가 재시도할 때마다
            // ContentStreamProvider가 새 스트림을 열어준다(멀티파트 임시 파일에서 다시 읽음).
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(storageKey)
                            .contentType(upload.mimeType())
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromContentProvider(
                            ContentStreamProvider.fromInputStreamSupplier(upload.content()),
                            upload.contentLength(),
                            upload.mimeType()));
        } catch (SdkException e) {
            log.error("[Evidence Store Failed] caseId={}, cause={}", caseId, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }

        return new StoredEvidence(storageKey, upload.contentLength());
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(storageKey)
                            .build(),
                    ResponseTransformer.toBytes()).asByteArray();
        } catch (NoSuchKeyException e) { //S3에 해당 storageKey에 해당하는 파일이 없을 경우
            throw new CustomException(ErrorCode.EVIDENCE_NOT_FOUND);
        } catch (SdkException e) { // 네트워크 단계나 S3 연동 문제
            log.error("[Evidence Load Failed] storageKey={}, cause={}", storageKey, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(storageKey)
                            .build());
        } catch (SdkException e) {
            log.error("[Evidence Delete Failed] storageKey={}, cause={}", storageKey, e.getMessage(), e);
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }
    }
}
