# Interview Guide

## 30초 요약

BorrowMe는 11인 해커톤 팀 프로젝트에서 시작한 물건 대여 API입니다. 포트폴리오에서는 본인 담당 범위인
상품 목록 N+1 개선, 예약 재고 정합성, 알림 흐름을 분리해 설명합니다. p95/처리량 수치는 원본 README
기록 기준이고, 현재 자동 검증은 query-count guard와 reservation concurrency test입니다.

## 예상 질문과 답변 포인트

| 질문 | 답변 포인트 |
| --- | --- |
| 팀 프로젝트인데 어디까지 직접 했나요? | 예약 시스템, Pessimistic Lock 기반 동시성 제어, 상품 목록 N+1 개선, k6 시나리오 정리를 담당 범위로 분리합니다. |
| p95 1,010ms -> 23ms를 지금 재현할 수 있나요? | 원본 README 기록 기준입니다. 현재 repo에서는 query-count guard로 회귀를 막고, 2026-05-22 product-listing/search/concurrent-reserve local snapshot은 별도 raw artifact로 분리했습니다. |
| N+1은 어떻게 막았나요? | 목록/검색/단건 응답에 필요한 Product/User/Role/Hashtag를 fetch join으로 가져오고, Hibernate statistics와 MockMvc로 SQL 수와 응답 변환 경로를 guard합니다. |
| 검색 부하에서 500이 난다면 어디를 보나요? | 같은 사용자/키워드 recent search 동시 저장이 중복 row를 만들 수 있으므로 `(user_id, keyword)` unique constraint와 MySQL upsert, `ProductQueryTest` concurrency guard를 확인합니다. |
| 예약 재고 초과는 어떻게 막나요? | Product row를 `PESSIMISTIC_WRITE`로 잠그고 재고 확인/차감/예약 저장을 하나의 transaction에서 처리합니다. |
| 왜 `entityManager.detach()`를 쓰나요? | 예약 서비스에서 기존 Product entity를 detach한 뒤 lock query로 재조회해 L1 cache가 재고 재조회 결과를 가리지 않게 합니다. |
| 아직 약한 부분은 무엇인가요? | 상품 검색, recent search, 인증 상품 목록 follow-aware guard, ranking data path guard, Flyway baseline validation은 추가했지만 HTTP 렌더링 성능과 운영 성능 반복 측정은 아직 남아 있습니다. |

## 피해야 할 표현

- 팀 전체 서비스를 혼자 설계/구현했다고 말하지 않습니다.
- 원본 README 수치를 현재 repo에서 새로 재측정한 값처럼 말하지 않습니다.
- 운영 성능이나 autoscaling을 주장하지 않습니다.
