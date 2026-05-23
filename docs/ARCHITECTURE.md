# BorrowMe Architecture

이 문서는 BorrowMe를 백엔드 포트폴리오 관점에서 설명하기 위한 README-level 전체 아키텍처 요약입니다.
범위는 후보자가 설명할 수 있는 핵심 흐름인 상품 목록 조회, 예약 정합성, 알림 흐름과 그 검증 경계입니다.

![BorrowMe 전체 아키텍처](assets/architecture/overall-architecture.svg)

## 범위

| 영역 | 설명 | 증거 경계 |
| --- | --- | --- |
| 상품 목록 조회 | Product API와 Product Service가 상품, 사용자, 이미지, 팔로우 여부를 조회해 목록 응답을 구성합니다. | query-count guard와 local k6 snapshot으로 설명합니다. |
| 예약 정합성 | Reservation API와 Reservation Service가 수량 기반 예약을 처리하고 재고 차감 경로에서 Pessimistic Lock을 사용합니다. | Testcontainers 동시 예약 시나리오와 k6 local snapshot으로 설명합니다. |
| 알림 흐름 | Notification API와 Notification Service가 댓글, 답글, 팔로우 등 사용자 이벤트 알림을 다룹니다. | 구현된 API/service 흐름을 설명하며 운영 메시지 브로커나 delivery SLO를 주장하지 않습니다. |
| 데이터 저장소 | MySQL에 상품, 예약, 알림, 사용자 관계 데이터를 저장합니다. | 로컬/테스트 데이터베이스 기준이며 운영 토폴로지 주장이 아닙니다. |
| 테스트 경계 | Testcontainers MySQL로 query guard, 예약 동시성, migration baseline 검증을 수행합니다. | 자동 테스트와 보존된 artifact 범위까지만 측정 완료로 말합니다. |

## 핵심 흐름

1. Client 요청은 Spring Security/JWT 인증 필터를 거쳐 Product, Reservation, Notification API로 전달됩니다.
2. Product Service는 목록/검색 응답을 만들 때 반복 쿼리 회귀를 줄이도록 조회 경로를 guard합니다.
3. Reservation Service는 동시에 예약해도 재고가 음수가 되지 않도록 재고 차감 경로를 잠금 기반으로 처리합니다.
4. Notification Service는 댓글/답글/팔로우 이벤트로 생기는 알림 조회와 읽음 처리를 담당합니다.
5. MySQL Testcontainers는 실제 MySQL 기반으로 query-count와 concurrency 시나리오를 검증하는 별도 테스트 경계입니다.

## 설계 판단

| 판단 | 이유 | 현재 검증 |
| --- | --- | --- |
| 계층형 Spring Boot API로 핵심 도메인 흐름 분리 | controller, service, repository 책임을 분리해 상품/예약/알림 설명 범위를 명확히 합니다. | 단위/통합 테스트와 README 구조 설명 |
| 예약 재고 차감에 Pessimistic Lock 사용 | 같은 상품에 동시 예약이 몰릴 때 재고 불일치를 막는 것이 우선입니다. | Testcontainers 동시 예약 시나리오 검증 |
| 상품 목록 조회에 query-count guard 유지 | N+1 회귀는 포트폴리오 핵심 주장과 직접 연결되므로 테스트로 감시합니다. | Product query guard와 보존된 k6 artifact |
| Testcontainers MySQL을 검증 DB로 사용 | H2와 다른 MySQL 동작을 회피하고 실제 DB 기준의 query/concurrency 검증을 남깁니다. | Gradle test/build에서 실행되는 MySQL 기반 테스트 |

## 주장 경계

- 이 다이어그램은 구현된 핵심 흐름과 검증 대상 경계를 설명하기 위한 단순화된 구조도이며, 운영 배포 토폴로지나 production SLO를 주장하지 않습니다.
- 원본 README의 상품 목록 p95 `23ms`는 raw artifact가 없는 historical original record이며 현재 측정 완료 수치가 아닙니다.
- 현재 raw artifact가 있는 상품 목록 값은 2026-05-23 clean repeat3 local snapshot p95 `358.1088ms`입니다.
- 두 p95 값은 같은 조건의 반복 측정이나 현재 개선 폭으로 직접 비교하지 않습니다.
