# Design

BorrowMe는 11인 해커톤 팀 프로젝트에서 시작한 물건 대여 REST API입니다. 포트폴리오에서는 서비스 전체를
혼자 설계했다는 주장이 아니라, 본인이 정리한 예약 정합성, 상품 목록 조회 성능, 알림 흐름의 설계 경계를
분리해 설명합니다.

## 문제 구간

| 구간 | 설계 판단 |
| --- | --- |
| 상품 목록 조회 | 목록 응답에 필요한 Product, User, Role, Hashtag 연관 데이터를 fetch join으로 모아 N+1 회귀를 막습니다. |
| 상품 검색 조회 | 검색 결과 DTO 변환에 필요한 Product, User, Role, Hashtag 연관 데이터를 fetch join으로 모아 검색 결과 N+1 회귀를 막습니다. |
| 예약 재고 차감 | `PESSIMISTIC_WRITE` row lock으로 같은 product 재고를 직렬화합니다. |
| 예약 재고 차감 구현 | 예약 서비스는 기존 Product entity를 detach한 뒤 lock query로 재조회해 Hibernate L1 cache가 lock 검증을 흐리지 않게 합니다. |
| 팀 프로젝트 주장 경계 | 본인 담당 범위와 팀 전체 구현 범위를 분리해 문서화합니다. |

## 상품 목록 조회

Before 원본 코드는 repository에 보존되어 있지 않습니다. 현재 문서는 원본 README 기록의 p95/처리량/쿼리
수 변화를 historical original record로 재사용하고, 자동 검증은 `ProductQueryTest`의 query-count guard에 둡니다.
현재 raw artifact가 있는 상품 목록 수치는 2026-05-23 clean repeat3 local k6 snapshot p95 `358.1088ms`로
별도 분리합니다.

```text
Product 목록 조회
-> 응답 변환 중 연관 데이터 반복 접근
-> JOIN FETCH 기반 조회 경로로 재구성
-> service-level SQL 1회, security filter 제외 API 변환 경로 SQL 3회 이하 guard
```

## 예약 정합성

예약은 상품 row를 비관적 락으로 조회한 뒤 재고를 차감합니다.

```text
POST /api/products/{productId}/reserve
-> Product row lock
-> availableQuantity 확인
-> Reservation 저장
-> Product quantity 감소
```

`ReservationConcurrencyTest`는 재고 50개 상품에 100개 동시 예약을 시도해 성공 50건, 예약 row 50건,
최종 재고 0을 검증합니다.

## 한계

- 상품 검색, 해시태그 검색, 운동 추천 exercise hashtag 조회, bulk follow lookup, 인증 상품 목록 follow-aware 응답, ranking data path query-count는 자동 guard를 추가했습니다. ranking HTTP 렌더링 성능은 아직 benchmark로 주장하지 않습니다.
- Flyway는 현재 schema baseline validation으로만 설명하고, 기존 production migration 이력을 복원했다고 주장하지 않습니다.
- 원본 k6 raw artifact가 없는 p95/처리량 수치는 원본 README 기록 기준으로만 설명합니다. clean repeat3
  p95 `358.1088ms`를 원본 README After p95 `23ms`의 재현 또는 대체 수치로 말하지 않습니다.
