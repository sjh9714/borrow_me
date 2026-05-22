# 상품 목록 조회 N+1 개선

이 문서는 README에 남아 있는 상품 목록 조회 개선 기록과 2026-05-22 실행 시점 worktree의 로컬 snapshot을
분리해 설명합니다.

## 문제

상품 목록 조회는 서비스 첫 화면과 가까운 API입니다. 상품 100개를 조회할 때 이미지, 예약,
상태 같은 연관 데이터를 상품별로 반복 조회하면 N+1 쿼리가 발생하고 p95 응답 시간이 크게
늘어납니다.

## Before

| 항목 | 값 |
| --- | --- |
| 조건 | 상품 목록 조회, 30 VU, 30초 |
| p95 응답 시간 | 1,010ms |
| 처리량 | 30 req/s |
| DB 쿼리 | 201회 |

## 원인

- Product 목록 조회 이후 연관 데이터가 상품별로 반복 조회됐습니다.
- 목록 API에서 필요한 이미지/예약/상태 정보를 한 번에 가져오지 못했습니다.
- 팀 프로젝트의 초기 구조에서는 기능 구현 속도가 우선되어 조회 경로 최적화가 늦게 분리됐습니다.

## Before / After 코드 경계

원본 Before 코드는 현재 repository에 보존되어 있지 않습니다. 따라서 이 문서는 기존 README에 남아 있던
`201회 -> 3회` 기록을 재사용하되, Before 코드를 실제 코드처럼 재구성하지 않습니다.

Before에서 문제가 된 형태는 아래와 같은 접근 패턴입니다.

```txt
Product 목록 조회
-> 각 Product의 user / roles / hashtags / comments / reservation 상태를 응답 변환 중 반복 접근
-> 상품 수에 비례해 SQL 증가
```

위 패턴은 원본 README 기록을 설명하기 위한 문제 경계입니다. 현재 자동 query-count guard가 직접
검증하는 최적화 scope는 `Product + User + roles + Hashtag` fetch join과 security filter를 제외한
상품 목록 응답 변환 경로입니다. 추가로 인증 사용자의 상품 목록 응답에서 팔로우 여부 true/false와 SQL
5회 이하를 guard하고, ranking data path의 상위 사용자/최근 상품/팔로우 여부 조합도 SQL 5회 이하로
guard합니다. comments, reservation, ranking HTTP 렌더링 성능 전체를 포괄한다고 주장하지는 않습니다.

현재 After 경로에서 repository에 남아 있는 핵심 코드는 `ProductRepository.findAllWithUserAndHashtags()`입니다.

```java
@Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.user u LEFT JOIN FETCH u.roles LEFT JOIN FETCH p.hashtags")
List<Product> findAllWithUserAndHashtags();
```

`ProductQueryTest`는 이 service-level 조회 경로가 SQL 1회로 유지되는지, 그리고 security filter를 제외한
`GET /api/products` 응답 변환 경로가 SQL 3회 이하를 유지하는지 guard합니다. 인증 사용자의
`GET /api/products` 응답은 팔로우 여부 true/false와 SQL 5회 이하를 별도 guard로 확인합니다.

## 해결

- 상품 목록에서 필요한 `Product`, `User`, `User.roles`, `Hashtag` 연관 데이터를 `JOIN FETCH`로 함께 조회하도록 재구성했습니다.
- 현재 자동 회귀 검증은 service-level 조회 경로의 lazy loading 재발을 막는 데 초점을 둡니다.
- 원본 README 기록 기준 쿼리 수와 p95를 Before/After로 남겨 README 주장과 연결했습니다.
- `ProductQueryTest`에서 Hibernate statistics로 `getAllProductsWithDetails()`의 조회 SQL이 1회로
  유지되는지 회귀 검증합니다.
- 같은 테스트에서 security filter를 제외한 `GET /api/products` 응답 변환 경로가 문서화된 After
  기준인 3회 이하 SQL을 유지하는지도 guard합니다.
- 인증 사용자의 `GET /api/products` 응답에서 팔로우 여부 true/false가 포함되고 SQL 5회 이하로
  유지되는지도 guard합니다.
- ranking data path에서 상위 사용자, 최근 상품, 팔로우 여부 조회 조합이 SQL 5회 이하로 유지되는지도
  guard합니다.

## After

| 항목 | 값 |
| --- | --- |
| 조건 | 상품 목록 조회, 30 VU, 30초 |
| p95 응답 시간 | 23ms |
| 처리량 | 253 req/s |
| DB 쿼리 | 3회 |

## 해석

- 원본 README 기록 기준 p95 응답 시간은 1,010ms에서 23ms로 줄었습니다.
- 원본 README 기록 기준 처리량은 30 req/s에서 253 req/s로 증가했습니다.
- 원본 README 기록 기준 DB 쿼리 수는 201회에서 3회로 줄었습니다.

이 수치는 상품 목록 조회 경로의 개선 결과입니다. 전체 서비스 성능이나 운영 환경 성능으로
확장해 주장하지 않습니다.

## 현재 로컬 재실행 snapshot

2026-05-22에 해당 실행 시점의 worktree와 `k6/setup-data.sql` fixture로 product-listing 시나리오를 다시 실행해
raw artifact를 보존했습니다. 이 snapshot은 해당 실행 시점의 worktree가 k6 threshold를 통과한다는 근거이며, 원본
README의 Before/After 개선 폭과 동일한 조건의 재현이라고 주장하지 않습니다.

| 항목 | 값 |
| --- | --- |
| 시나리오 | `product-listing`, 30 VU, 30초 |
| 앱/DB | local profile app on `localhost:5001`, Docker MySQL `shop`, `k6/setup-data.sql` fixture |
| p95 응답 시간 | 50.5ms |
| HTTP 요청 처리량 | 229.29 req/s |
| HTTP 실패율 | 0.00% |
| checks | 20,985 / 20,985 성공 |
| raw artifact | `docs/evidence/k6/20260522T070732Z-product-listing/` |

## 측정 한계

- Before/After 수치는 원본 README에 남아 있던 k6/쿼리 개선 기록 기준입니다.
- 현재 로컬 재실행 snapshot은 해당 실행 시점의 worktree와 fixture 기준의 단일 local run이며, 운영 성능 claim으로
  확장하지 않습니다.
- artifact metadata에는 당시 `git status`가 포함되어 있으며, clean commit 기준 반복 측정은 추가 측정 예정입니다.
- 자동 query-count guard는 `getAllProductsWithDetails()`, security filter를 제외한
  `GET /api/products` 응답 변환 경로, 인증 상품 목록 follow-aware 응답 경로, 상품 검색, 해시태그
  검색, ranking data path와 `GET /ranking` handler/model assembly를 대상으로 합니다. 실제 템플릿
  렌더링 시간이나 HTTP 성능 수치까지 포괄하지는 않습니다.
- 원본 Before/After EXPLAIN 출력은 repository에 보존되어 있지 않습니다. 대신 현재 코드 기준 상품 목록
  조회 경로의 EXPLAIN artifact를 `docs/evidence/explain/20260522-product-list-query/`에 보존하고,
  `ProductQueryTest`에서 해당 query shape를 MySQL Testcontainers로 확인합니다. 이 artifact는 과거
  개선 폭이나 운영 성능 근거로 사용하지 않습니다.
- 실행 환경의 CPU, memory, DB 버전 등은 현재 문서에서 확인되지 않아 추가하지 않습니다.
- 평균, 표준편차, 신뢰구간은 계산하지 않았습니다.

## 면접에서 설명할 질문

| 질문 | 답변 포인트 |
| --- | --- |
| 왜 N+1이 발생했나요? | 상품 목록 이후 연관 데이터를 상품별로 반복 조회했습니다. |
| 쿼리를 3회로 줄인 기준은 무엇인가요? | 목록 응답에 필요한 연관 데이터를 fetch join으로 묶고, README의 Before/After 수치와 service-level query-count guard를 분리해 설명합니다. |
| p95 개선을 어떻게 해석해야 하나요? | 운영 성능 주장이 아니라 원본 README에 같은 시나리오로 기록된 Before/After 상대 개선입니다. |
