# Team Contribution

BorrowMe는 개인 프로젝트가 아니라 11인 해커톤 팀 프로젝트에서 시작했습니다. 포트폴리오에서는
서비스 전체를 혼자 구현했다고 주장하지 않고, 본인이 담당한 백엔드 문제 해결 범위만 분리해
설명합니다.

## 팀 구성

| 역할 | 인원 |
| --- | --- |
| PM | 2명 |
| Designer | 1명 |
| Frontend | 2명 |
| Backend | 6명 |

## 본인 담당 범위

| 영역 | 담당 내용 |
| --- | --- |
| 예약 시스템 | 수량 기반 예약, 예약 취소, 재고 감소 흐름 |
| 동시성 제어 | Pessimistic Lock 기반 재고 초과 예약 방지 |
| 조회 성능 | 상품 목록 N+1 개선과 k6 Before/After 기록 |
| 알림 | 댓글/답글/팔로우 알림과 읽음 처리 흐름 |
| 성능 테스트 | 상품 목록, 검색, 동시 예약 k6 시나리오 정리 |

## 검증 근거가 있는 주장

- 상품 목록 조회 N+1 개선: 원본 README 기록 기준 p95 1,010ms -> 23ms, DB 쿼리 201회 -> 3회.
  현재 repo에서 재측정한 수치는 아니며 자동 검증은 query-count guard 범위입니다.
- 인증 상품 목록 팔로우 여부 응답: `GET /api/products`에서 팔로우 true/false를 포함하고 SQL 5회 이하로
  유지되는지 Testcontainers + MockMvc로 guard합니다.
- 랭킹 데이터 경로: 상위 사용자, 최근 상품, 팔로우 여부 조합이 SQL 5회 이하로 유지되는지
  Testcontainers + Hibernate statistics로 guard합니다.
- 동시 예약 정합성: 재고 50개 / 100 VU에서 예약 성공 50건, 최종 재고 0.
- recent search 동시 저장: 같은 사용자/키워드 동시 요청이 중복 row를 만들지 않는지 Testcontainers로 guard합니다.
- k6 시나리오 artifact: 상품 목록 clean repeat3 p95 358.1088ms, 검색, 동시 예약 local snapshot의 raw output과
  metadata를 보존했습니다. 원본 README의 상품 목록 After p95 23ms와 별도 증거 범주입니다.

## 코드 담당 범위로만 설명하는 항목

- 알림: 댓글/답글/팔로우 알림과 읽음 처리 흐름을 담당 범위로 설명합니다. 별도 성능/부하 claim은 하지 않습니다.
- 해커톤 팀 프로젝트 이후 코드 정합성과 성능 개선을 이어간 맥락은 설명하되, 서비스 전체를 혼자 구현했다고 말하지 않습니다.

## 주장하지 않는 것

- 서비스 전체를 혼자 설계/구현했다는 주장.
- 장기 운영 성능이나 production autoscaling.
- 모든 기능의 Testcontainers 기반 회귀 테스트가 완비됐다는 주장.
- Flyway로 기존 production migration 이력을 모두 복원했다는 주장.

## 다음 개선 과제

1. 인증 filter까지 포함한 end-to-end follow-aware 성능 측정은 별도 raw artifact로 보존합니다.
2. 랭킹 HTTP 렌더링 성능은 별도 raw artifact로 보존합니다.
3. [MIGRATION_STRATEGY.md](MIGRATION_STRATEGY.md)를 기준으로 후속 schema 변경을 baseline 이후 migration으로 분리합니다.
4. 도메인별 패키지 구조로 점진적으로 정리합니다.
