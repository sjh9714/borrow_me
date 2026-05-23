# BorrowMe

![CI](https://github.com/sjh9714/borrow_me/actions/workflows/ci.yml/badge.svg)

BorrowMe는 **가톨릭대학교 GGUM 해커톤에서 시작한 11인 팀 프로젝트**로, 대학생 간 물건 대여 흐름을 다루는 Spring Boot REST API입니다. 팀에서는 상품 등록, 이미지 업로드, 예약, 팔로우, 댓글/답글, 좋아요, 검색, 랭킹, 알림, 이메일 인증을 하나의 백엔드 프로젝트 안에서 구현했습니다.

## 프로젝트 맥락

이 저장소는 개인 프로젝트가 아니라 1박 2일 해커톤에서 시작한 팀 프로젝트를 이후 코드 정합성 개선과 성능 최적화까지 이어 간 백엔드 포트폴리오입니다. README에서는 팀 프로젝트의 서비스 맥락과 함께, 백엔드에서 다룬 예약 정합성·조회 성능·알림 흐름을 중심으로 정리합니다.

| 구분 | 내용 |
| --- | --- |
| 이벤트 | 가톨릭대학교 GGUM 해커톤 (1박 2일, 2024) |
| 팀 구성 | 11명: PM 2명, 디자이너 1명, 프론트엔드 2명, 백엔드 6명 |
| 본인 담당 | 예약 시스템, Pessimistic Lock 기반 동시성 제어, N+1 개선, k6 성능 테스트, 알림 시스템 |
| 프로젝트 방향 | 대학생 간 유휴 물건을 쉽게 빌려주고 빌릴 수 있는 물건 대여 플랫폼 |

해커톤 종료 후에는 코드 정합성 개선과 성능 최적화를 이어 가며 포트폴리오 수준으로 다듬었습니다.

## 문제 의식

대여 서비스는 단순 CRUD보다 더 많은 상태 관리를 요구합니다. 상품 재고를 동시에 예약할 때 수량이 깨지지 않아야 하고, 상품·사용자·해시태그·팔로우 정보를 함께 보여줄 때 N+1 쿼리를 줄여야 합니다. 이 저장소는 예약 정합성과 조회 성능 개선을 포트폴리오 주제로 정리합니다.

## 이 레포가 증명하는 것

| 상태 | 항목 | 근거 |
| --- | --- | --- |
| 원본 측정 기록 | 상품 목록 p95 1,010ms -> 23ms, 처리량 30 req/s -> 253 req/s | 원본 README 기록 기준, raw artifact 없음 |
| 현재 로컬 재실행 snapshot | 상품 목록 product-listing clean repeat3 p50 121.6ms, p95 358.1ms, p99 557.66ms, HTTP 실패율 0.00%, checks 10,683 / 10,683 성공 | `docs/evidence/k6/20260523T004642Z-product-listing/` |
| 이전 로컬 재실행 snapshot | 상품 목록 product-listing p95 50.5ms, HTTP 실패율 0.00%, checks 20,985 / 20,985 성공 | `docs/evidence/k6/20260522T070732Z-product-listing/` |
| 현재 로컬 재실행 snapshot | 검색 search p95 133.03ms, p99 182.01ms, HTTP 실패율 0.00%, checks 10,976 / 10,976 성공 | `docs/evidence/k6/20260522T073727Z-search/` |
| 현재 로컬 재실행 snapshot | 동시 예약 concurrent-reserve 성공 50건 / 재고 부족 실패 50건 / 최종 재고 0 | `docs/evidence/k6/20260522T074050Z-concurrent-reserve/` |
| 자동 회귀 검증 | 상품 목록 원본 쿼리 기록 201회 -> 3회, 현재 상품 목록/검색/단건 조회/recent search SQL 회귀 guard와 현재 query shape EXPLAIN 확인 | 원본 README 기록 + `ProductQueryTest` query-count/concurrency/EXPLAIN guard |
| 시나리오 검증 | 인증 `GET /api/products`에서 팔로우 여부 true/false 응답과 SQL 5회 이하 query-count guard | `ProductQueryTest.getProductsApi_withAuthentication_shouldBatchFollowLookup` |
| 시나리오 검증 | ranking data path에서 상위 사용자, 최근 상품, 팔로우 여부 조합을 SQL 5회 이하로 유지 | `ProductQueryTest.rankingDataPath_shouldKeepQueryCountBounded` |
| 시나리오 검증 | `GET /ranking` no-op view 기반 handler/model assembly에서 topUsers/currentUser/recentProducts/followed flag를 구성하고 SQL 6회 이하로 유지. 실제 템플릿 렌더링 성능 benchmark는 아님 | `ProductQueryTest.rankingPage_shouldRenderModelAndKeepQueryCountBounded` |
| 시나리오 검증 | 재고 50개 상품에 100명 동시 예약 시 성공 50건, 최종 재고 0 / 재고 부족 실패 시 row·재고 불변 | `ReservationConcurrencyTest` |
| 시나리오 검증 | Flyway baseline schema migration과 migration history 생성 검증 | `V1__baseline_schema.sql`, `FlywayMigrationTest` |
| 문서화 완료 | schema migration 전략 문서화, 반복 측정 환경 보존, 운영 성능 claim 분리 | `docs/MIGRATION_STRATEGY.md`, `docs/LIMITATIONS.md` |

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
| Persistence | Spring Data JPA, MySQL, H2 for lightweight tests, MySQL Testcontainers for evidence tests |
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

테스트는 Java 17에서 Gradle로 실행합니다. Gradle 자체가 최신 JDK에서 먼저 뜨면 buildscript 분석 단계에서 실패할 수 있으므로, 로컬에서는 JDK 17을 명시하는 편이 안전합니다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test
```

## 성능 테스트

`k6/`에는 상품 목록, 검색, 동시 예약 시나리오가 정리되어 있습니다.

```bash
BASE_URL=http://localhost:5000 k6 run k6/test-product-listing.js
BASE_URL=http://localhost:5000 k6 run k6/test-search.js
BASE_URL=http://localhost:5000 k6 run k6/test-concurrent-reserve.js
```

### 원본 기록 기반 성능·정합성 하이라이트 (현재 재측정 아님)

원본 README에 남아 있던 k6와 쿼리 개선 기록 기준입니다. 상품 목록과 검색의 과거 k6 raw artifact는
보존되어 있지 않으므로, 현재 repo에서는 query-count guard와 재실행용 k6 시나리오로 회귀를 방지합니다.

| 테스트 | 지표 | Before | After | 근거 / 주의 |
| --- | --- | --- | --- | --- |
| 상품 목록 조회 (30 VU, 30초) | p95 응답시간 | 1,010ms | 23ms | 원본 README 기록, 별도 raw artifact 없음, 현재 재측정 아님 |
| 상품 목록 조회 (30 VU, 30초) | 처리량 | 30 req/s | 253 req/s | 원본 README 기록, 별도 raw artifact 없음, 현재 재측정 아님 |
| 상품 목록 조회 (상품 100개) | DB 쿼리 | 201회 | 3회 | JOIN FETCH + query-count guard |
| 동시 예약 (100 VU, 재고 50개) | 예약 성공 | 100건 (전부 성공) | 50건 | 재고 초과 예약 방지 |
| 동시 예약 (100 VU, 재고 50개) | 최종 재고 | 불일치 | 0 | 재고 초과 예약 방지 시나리오 검증 |
| 검색 (30 VU, 30초) | p95 응답시간 | - | 72ms | 원본 README 기록, 별도 raw artifact 없음, 현재 재측정 아님 |

새 성능 수치를 주장하려면 k6 실행 로그, summary, dataset 조건을 함께 보존한 뒤 이 표를 갱신합니다.

### 현재 로컬 재실행 snapshot

2026-05-23에 `main`의 clean commit 기준으로 상품 목록 `product-listing` clean repeat3 snapshot을 추가했습니다.
2026-05-22에는 해당 실행 시점의 worktree와 `k6/setup-data.sql` fixture로 상품 목록 `product-listing`, 검색 `search`,
동시 예약 `concurrent-reserve` 시나리오를 재실행했고, raw artifact를 보존했습니다.

| 시나리오 | 조건 | 핵심 결과 | raw artifact |
| --- | --- | --- | --- |
| product-listing clean repeat3 | 30 VU, 30초, local profile app on `localhost:5001`, throwaway Docker MySQL `shop` on host port 3307 | p50 121.6ms, p95 358.1ms, p99 557.66ms, HTTP 실패율 0.00%, checks 10,683 / 10,683 성공, query count는 k6 artifact에 없음 | `docs/evidence/k6/20260523T004642Z-product-listing/` |
| product-listing previous snapshot | 30 VU, 30초 | p95 50.5ms, HTTP 실패율 0.00%, checks 20,985 / 20,985 성공 | `docs/evidence/k6/20260522T070732Z-product-listing/` |
| search | 30 VU, 30초 | p95 133.03ms, p99 182.01ms, HTTP 실패율 0.00%, checks 10,976 / 10,976 성공 | `docs/evidence/k6/20260522T073727Z-search/` |
| concurrent-reserve | 100 VU, 재고 50개 | 예약 성공 50건, 재고 부족 실패 50건, 최종 재고 0, 예상 밖 오류 0건 | `docs/evidence/k6/20260522T074050Z-concurrent-reserve/` |

이 snapshot은 해당 실행 시점의 worktree가 각 k6 threshold를 통과한다는 근거입니다. 원본 README의 Before/After p95
23ms나 검색 p95 72ms를 같은 조건으로 재현했다는 뜻은 아니며, 운영 성능 claim으로 확장하지 않습니다.
2026-05-23 product-listing clean repeat3 metadata에는 실행 전 git clean 상태와 실행 commit을 별도로 보존했습니다.

예약 race condition은 `@Lock(PESSIMISTIC_WRITE)`와 `SELECT FOR UPDATE`로 행 레벨 잠금을 적용했고, Hibernate L1 캐시가 잠금을 우회하지 않도록 `entityManager.detach()`를 함께 사용했습니다.
검색 recent search는 `(user_id, keyword)` unique constraint와 MySQL upsert로 같은 사용자/키워드 동시 요청이 중복 row를 만들지 않도록 보강했습니다.

## 증거 문서

| 문서 | 내용 |
| --- | --- |
| [docs/DESIGN.md](docs/DESIGN.md) | 예약 정합성, 상품 목록 조회 경로, 팀 프로젝트 주장 경계 |
| [docs/PERF_RESULT.md](docs/PERF_RESULT.md) | 상품 목록/예약 성능 관련 기록 index |
| [docs/PRODUCT_LIST_PERF.md](docs/PRODUCT_LIST_PERF.md) | 상품 목록 조회 N+1 개선, p95/처리량/쿼리 수 Before/After, 현재 query shape EXPLAIN artifact |
| [docs/RESERVATION_CONSISTENCY.md](docs/RESERVATION_CONSISTENCY.md) | 재고 50개 / 100 VU 동시 예약 정합성 개선 |
| [docs/TESTING.md](docs/TESTING.md) | Product 목록/검색 query guard와 reservation concurrency Testcontainers 검증 범위 |
| [docs/TEAM_CONTRIBUTION.md](docs/TEAM_CONTRIBUTION.md) | 11인 팀 프로젝트에서 본인 담당 범위와 포트폴리오 주장 경계 |
| [docs/MIGRATION_STRATEGY.md](docs/MIGRATION_STRATEGY.md) | Flyway baseline validation과 아직 주장하지 않는 migration 범위 |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | 상품 목록 성능 회귀, 예약 재고 불일치, 검색 성능 claim 확인 절차 |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | query-count 자동 회귀 테스트, Flyway, 운영 성능 claim의 한계 |
| [docs/INTERVIEW_GUIDE.md](docs/INTERVIEW_GUIDE.md) | 면접에서 설명할 핵심 질문과 안전한 답변 |

## 참고 사항

- 이 저장소는 백엔드 API 중심이며 프론트엔드 코드는 포함하지 않습니다.
- `application-prod.properties`는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`를 외부에서 주입하는 배포용 설정입니다.
