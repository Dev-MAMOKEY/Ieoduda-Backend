# 파일 구조
```
domain/
├── global/                               ← 전 도메인 공통 (BaseEntity, enum)
│   ├── entity/
│   │   ├── BaseCreatedAtEntity.java      
│   │   └── BaseUpdatedAtEntity.java     
│   ├── exception/                        ← 전역 예외 처리
│   │   ├── CustomException.java         
│   │   ├── ErrorCode.java                
│   │   └── GlobalExceptionHandler.java  
│   └── rsdata/                           ← 공통 API 응답
│       └── RsData.java                   
│
├── account/                              ← 계정 & 동의
│   └── entity/
│       ├── User.java                     
│       ├── Consent.java                
│       └── ConsentType.java              
│
├── plan/                                 ← 계획 생성 · 삶의 구역 · AI 구조화
│   └── entity/
│       ├── Plan.java                     
│       ├── PlanStatus.java               
│       ├── PlanVersion.java            
│       ├── LifeArea.java                 
│       ├── LifeAreaCategory.java         
│       ├── Item.java                     
│       ├── ItemStatus.java               
│       └── DisclosureScope.java          
│
├── recipient/                            ← 역할 담당자
│   └── entity/
│       ├── Recipient.java                
│       ├── RoleType.java                 
│       └── AcceptanceStatus.java         
│
├── confirmer/                            ← 지정 확인자 · 이의제기 연락처
│   └── entity/
│       ├── Confirmer.java                
│       ├── Relationship.java             
│       ├── ReportStatus.java             
│       └── DisputeContact.java           
│
├── stage/                                ← 충돌 점검 · 발송 순서
│   └── entity/
│       ├── Dependency.java              
│       ├── HandoverStage.java            
│       └── HandoverStageStatus.java      
│
├── handoffcheck/                         ← 생전 인계 점검 (선택기능)
│   └── entity/
│       ├── HandoffCheck.java             
│       └── HandoffCheckResponse.java    
├── releasecase/                          ← 사망 확인 · 대기기간 · 이의제기
│   └── entity/
│       ├── ReleaseCase.java              
│       ├── ReleaseCaseStatus.java        
│       ├── Objection.java               
│       └── ObjectionStatus.java          
│
├── evidence/                             ← 공식 증빙 제출 · 검토 · 삭제감사
│   └── entity/
│       ├── Evidence.java                
│       └── EvidenceReviewStatus.java    
│
├── partner/                              ← 외부 법무·장례 파트너 검토
│   └── entity/
│       └── PartnerReviewer.java         
│
├── postaccess/                           ← 사후 인계 인증 · 패키지 문제 신고
│   └── entity/
│       ├── AccessToken.java             
│       ├── PackageIssue.java             
│       └── PackageIssueStatus.java       
│
└── audit/                                ← 운영자 감사
    └── entity/
        ├── EmailLog.java                
        └── EmailType.java                
```
