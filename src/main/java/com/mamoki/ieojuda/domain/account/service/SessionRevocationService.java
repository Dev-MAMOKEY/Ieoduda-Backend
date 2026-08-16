package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.entity.RefreshSession;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.RefreshSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// issue #56 - 계정에 영향을 주는 이벤트(이메일 변경, 계정 정지 등)가 생겼을 때 세션을 한 번에 정리한다.
// tokenVersion을 올려서 이미 발급된 Access Token은 만료 전이라도 필터에서 즉시 거부되게 하고,
// 아직 살아있는 Refresh Token들도 전부 폐기해서 재발급으로도 새 Access Token을 못 받게 막는다.
//
// revokeFamily()는 AuthService.refresh()가 재사용 탐지 직후 예외를 던지고 롤백하는 흐름 안에서 호출된다.
// 같은 트랜잭션에 남아있으면 그 롤백에 차단 자체가 같이 사라지므로(#48/#55에서 이미 겪은 것과 같은 함정),
// REQUIRES_NEW로 독립 트랜잭션에 커밋한다.
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final RefreshSessionRepository refreshSessionRepository;

    @Transactional
    public void revokeAllSessions(User user) {
        refreshSessionRepository.findByUser_UserIdAndRevokedAtIsNull(user.getUserId())
                .forEach(RefreshSession::revoke);
        user.incrementTokenVersion();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(String familyId) {
        refreshSessionRepository.findByFamilyIdAndRevokedAtIsNull(familyId)
                .forEach(RefreshSession::revoke);
    }
}
