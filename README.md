# BorrowMe

BorrowMe는 **가톨릭대학교 GGUM 해커톤에서 시작한 11인 팀 프로젝트**로, 대학생 간 물건 대여 흐름을 다루는 Spring Boot REST API입니다. 상품 등록, 이미지 업로드, 예약, 팔로우, 댓글/답글, 좋아요, 검색, 랭킹, 알림, 이메일 인증을 하나의 백엔드 프로젝트 안에서 구현했습니다.

## 프로젝트 맥락

이 저장소는 개인 프로젝트가 아니라 1박 2일 해커톤에서 시작한 팀 프로젝트를 이후 코드 정합성 개선과 성능 최적화까지 이어 간 백엔드 포트폴리오입니다. README에서는 팀 프로젝트의 서비스 맥락과 함께, 백엔드에서 다룬 예약 정합성·조회 성능·알림 흐름을 중심으로 정리합니다.

| 구분 | 내용 |
| --- | --- |
| 이벤트 | 가톨릭대학교 GGUM 해커톤 (1박 2일, 2024) |
| 팀 구성 | 11명: PM 2명, 디자이너 1명, 프론트엔드 2명, 백엔드 6명 |
| 본인 담당 | 예약 시스템, Pessimistic Lock 기반 동시성 제어, N+1 개선, k6 성능 테스트, 알림 시스템 |
| 프로젝트 방향 | 대학생 간 유휴 물건을 쉽게 빌려주고 빌릴 수 있는 물건 대여 플랫폼 |

## 문제 의식

대여 서비스는 단순 CRUD보다 더 많은 상태 관리를 요구합니다. 상품 재고를 동시에 예약할 때 수량이 깨지지 않아야 하고, 상품·사용자·해시태그·팔로우 정보를 함께 보여줄 때 N+1 쿼리를 줄여야 합니다. 이 저장소는 예약 정합성과 조회 성능 개선을 포트폴리오 주제로 정리합니다.

## 주요 기능

- JWT 기반 회원가입, 로그인, 토큰 인증
- 상품 CRUD, 이미지 업로드, 해시태그 추출/검색
- 수량 기반 상품 예약과 예약 취소
- 팔로우, 좋아요, 댓글, 답글
- 상품명, 설명, 사용자명, 운동/해시태그 기반 검색
- 최근 검색어 관리
- 팔로워 기반 랭킹
- 댓글/답글/팔로우 알림과 읽음 처리
- Gmail SMTP 이메일 인증
- Swagger OpenAPI 문서

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 3.1.5, Spring Web, Validation |
| Security | Spring Security, JWT, BCrypt |
| Persistence | Spring Data JPA, MySQL, H2 for tests |
| Storage / Mail | AWS S3, Spring Mail |
| Docs / Test | springdoc-openapi, JUnit 5, Testcontainers, k6 |
| Build | Gradle |

## 구조

```text
src/main/java/com/ardkyer/borrowme/
├── config/       # Security, JWT, S3, Email, OpenAPI, 예외 처리
├── controller/   # 상품, 회원, 검색, 댓글, 알림, 팔로우 등 API
├── dto/          # 인증/회원 요청과 응답 DTO
├── entity/       # User, Product, Reservation, Comment, Follow 등
├── repository/   # JPA Repository
├── security/     # JWT 필터와 PrincipalDetails
└── service/      # 도메인 서비스
```

## 실행 방법

로컬 프로필은 MySQL `shop` 데이터베이스를 사용합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

로컬 실행에 필요한 대표 환경 변수는 다음과 같습니다.

| 변수 | 용도 |
| --- | --- |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 정보 |
| `JWT_SECRET` | JWT 서명 키 |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 업로드 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail SMTP |

테스트는 Gradle로 실행합니다.

```bash
./gradlew test
```

## 성능 테스트

`k6/`에는 상품 목록, 검색, 동시 예약 시나리오가 정리되어 있습니다.

```bash
k6 run k6/test-product-listing.js
k6 run k6/test-search.js
k6 run k6/test-concurrent-reserve.js
```

기존 README의 성능 기록은 상품 목록 조회에서 JOIN FETCH와 배치 조회를 적용해 쿼리 수를 줄인 사례, 예약 처리에서 pessimistic lock으로 재고 race condition을 다룬 사례를 중심으로 정리되어 있습니다.

## 참고 사항

- 이 저장소는 백엔드 API 중심이며 프론트엔드 코드는 포함하지 않습니다.
- `application-prod.properties`는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`를 외부에서 주입하는 배포용 설정입니다.
