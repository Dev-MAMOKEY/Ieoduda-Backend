package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;

public interface EvidenceStorageClient {

    StoredEvidence store(Long caseId, EvidenceUpload upload);

    byte[] load(String storageKey);

    void delete(String storageKey);
}
