package com.mamoki.ieojuda.domain.recipient.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.dto.BackupRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.BackupRegisterResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientAcceptanceEmailResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientDetailResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientRegisterResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientUpdateRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientUpdateResponse;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// "역할 담당자 등록" 화면 - 승인된 항목(박스) 하나당 담당자 한 명을 배정하고, 등록과 동시에 역할 수락 이메일을 발송
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientService {

    private static final Set<Integer> ALLOWED_WAIT_HOURS = Set.of(168, 336, 504); // 7일/14일/21일

    private final ItemRepository itemRepository;
    private final RecipientRepository recipientRepository;
    private final PlanRepository planRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    @Transactional
    public RecipientBulkRegisterResponse registerAll(Long planId, RecipientBulkRegisterRequest request) {
        List<Item> items = validateAll(planId, request.recipients());

        List<RecipientRegisterResponse> responses = new ArrayList<>();
        for (int i = 0; i < request.recipients().size(); i++) {
            responses.add(registerOne(items.get(i), request.recipients().get(i)));
        }

        return new RecipientBulkRegisterResponse(responses);
    }

    // 이메일은 발송 후 되돌릴 수 없으므로, 저장·발송을 시작하기 전에 요청 전체를 먼저 검증한다
    private List<Item> validateAll(Long planId, List<RecipientRegisterRequest> requests) {
        Set<Long> requestedItemIds = new HashSet<>();
        List<Item> items = new ArrayList<>();

        for (RecipientRegisterRequest request : requests) {
            if (!requestedItemIds.add(request.itemId())) {
                throw new CustomException(ErrorCode.RECIPIENT_ALREADY_ASSIGNED);
            }

            Item item = findItem(planId, request.itemId());

            //  진입 조건: "구조화 항목이 승인된 후 진입"
            if (item.getStatus() != ItemStatus.APPROVED) {
                throw new CustomException(ErrorCode.ITEM_NOT_APPROVED);
            }
            // 항목(박스) 하나당 담당자 한 명 규칙
            if (item.getRecipient() != null) {
                throw new CustomException(ErrorCode.RECIPIENT_ALREADY_ASSIGNED);
            }
            if (!ALLOWED_WAIT_HOURS.contains(request.maxWaitHours())) {
                throw new CustomException(ErrorCode.INVALID_WAITING_PERIOD);
            }
            // 대체 담당자가 주 담당자와 동일 이메일이면 실제 대체 전환 시 의미가 없으므로 차단
            if (request.backup() != null && request.backup().email().equals(request.email())) {
                throw new CustomException(ErrorCode.BACKUP_RECIPIENT_EMAIL_DUPLICATED);
            }

            items.add(item);
        }

        return items;
    }

    // 담당자 저장 + 항목 배정 + 초대 토큰 발급 + 수락 이메일 발송
    // 이메일 발송 실패는 예외로 던지지 않는다 - 담당자 저장은 유지하고 건별 결과만 응답에 담는다
    private RecipientRegisterResponse registerOne(Item item, RecipientRegisterRequest request) {
        LifeArea lifeArea = item.getLifeArea();
        Plan plan = lifeArea.getPlan();
        DisclosureScope disclosureScope = item.getDisclosureScope();

        Recipient recipient = recipientRepository.save(Recipient.builder()
                .plan(plan)
                .lifeArea(lifeArea)
                .name(request.name())
                .email(request.email())
                .roleType(toRoleType(disclosureScope))
                .isBackup(false)
                .disclosureScope(disclosureScope)
                .maxWaitHours(request.maxWaitHours())
                .build());

        item.assignRecipient(recipient);

        EmailSendResult sendResult = issueInviteAndSend(recipient, toRoleName(disclosureScope));

        BackupRegisterResponse backupResponse = request.backup() == null
                ? null
                : registerBackup(plan, lifeArea, disclosureScope, recipient, request.backup());

        return RecipientRegisterResponse.of(
                recipient,
                item.getItemId(),
                sendResult.success(),
                sendResult.bounceType() == null ? null : sendResult.bounceType().name(),
                backupResponse
        );
    }

    // 대체 담당자 저장 + 초대 토큰 발급 + 수락 이메일 발송
    // 항목(Item)에는 배정하지 않는다 - 박스당 활성(주) 담당자 1명 규칙은 backupFor 관계로만 표현한다
    private BackupRegisterResponse registerBackup(Plan plan, LifeArea lifeArea, DisclosureScope disclosureScope,
                                                   Recipient primary, BackupRegisterRequest backupRequest) {
        Recipient backup = recipientRepository.save(Recipient.builder()
                .plan(plan)
                .lifeArea(lifeArea)
                .name(backupRequest.name())
                .email(backupRequest.email())
                .roleType(toRoleType(disclosureScope))
                .isBackup(true)
                .disclosureScope(disclosureScope)
                .maxWaitHours(null)
                .backupFor(primary)
                .build());

        EmailSendResult sendResult = issueInviteAndSend(backup, toRoleName(disclosureScope) + " (대체 담당자)");

        return BackupRegisterResponse.of(
                backup,
                sendResult.success(),
                sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    private EmailSendResult issueInviteAndSend(Recipient recipient, String roleName) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        recipient.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);
        return sendAcceptanceEmail(recipient, roleName, plainToken, expiresAt);
    }

    private EmailSendResult sendAcceptanceEmail(Recipient recipient, String roleName,
                                                 String plainToken, LocalDateTime expiresAt) {
        String secureLink = appProperties.getBaseUrl() + "/recipient-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                roleName,
                "역할 수락 여부를 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        return emailSender.send(recipient.getEmail(), content);
    }

    private RoleType toRoleType(DisclosureScope disclosureScope) {
        return switch (disclosureScope) {
            case FAMILY -> RoleType.FAMILY_MANAGER;
            case WORK -> RoleType.WORK_MANAGER;
            case RELATIONSHIP -> RoleType.RELATIONSHIP_MANAGER;
        };
    }

    private String toRoleName(DisclosureScope disclosureScope) {
        return switch (disclosureScope) {
            case FAMILY -> "가족 담당자";
            case WORK -> "업무 처리 담당자";
            case RELATIONSHIP -> "관계 정리 담당자";
        };
    }

    private Item findItem(Long planId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        if (!item.getLifeArea().getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    // "역할 점검" 화면 상세 - 이름 클릭 시 해당 담당자에게 배정된 항목 전체
    public RecipientDetailResponse getRecipient(Long userId, Long planId, Long assigneeId) {
        findOwnedPlan(userId, planId);
        Recipient recipient = findOwnedRecipient(planId, assigneeId);

        List<ItemResponse> items = itemRepository.findByRecipient_AssigneeIdOrderByItemIdAsc(assigneeId).stream()
                .map(ItemResponse::from)
                .collect(Collectors.toList());

        return RecipientDetailResponse.of(recipient, items);
    }

    // "수락 요청 다시 보내기" - 이미 수락/거절한 담당자에게는 재전송하지 않는다
    @Transactional
    public RecipientAcceptanceEmailResponse resendAcceptanceEmail(Long userId, Long recipientId) {
        Recipient recipient = recipientRepository.findById(recipientId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!recipient.getPlan().getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        if (recipient.getAcceptanceStatus() != AcceptanceStatus.PENDING
                && recipient.getAcceptanceStatus() != AcceptanceStatus.EXPIRED) {
            throw new CustomException(ErrorCode.RECIPIENT_RESEND_NOT_ALLOWED);
        }

        recipient.resetAcceptance();
        EmailSendResult sendResult = issueInviteAndSend(recipient, toRoleName(recipient.getDisclosureScope()));

        return RecipientAcceptanceEmailResponse.of(
                recipient,
                sendResult.success(),
                sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    // "담당자 수정하기" - 이름/이메일 수정. 이메일이 바뀌면 재검증이 필요하므로 수락 이메일을 다시 보낸다
    @Transactional
    public RecipientUpdateResponse updateRecipient(Long userId, Long planId, Long assigneeId, RecipientUpdateRequest request) {
        findOwnedPlan(userId, planId);
        Recipient recipient = findOwnedRecipient(planId, assigneeId);

        boolean emailChanged = recipient.updateContact(request.name(), request.email());

        EmailSendResult sendResult = emailChanged
                ? issueInviteAndSend(recipient, toRoleName(recipient.getDisclosureScope()))
                : null;

        return RecipientUpdateResponse.of(
                recipient,
                sendResult != null && sendResult.success(),
                sendResult == null || sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    // 로그인한 사용자가 자신의 계획만 조회할 수 있도록 검증 (불일치 시 존재 노출 방지를 위해 NOT_FOUND로 응답)
    private Plan findOwnedPlan(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private Recipient findOwnedRecipient(Long planId, Long assigneeId) {
        Recipient recipient = recipientRepository.findById(assigneeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (!recipient.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return recipient;
    }
}
