# Limitations

이 문서는 BorrowMe를 포트폴리오 증거 저장소로 읽을 때의 주장 경계를 정리합니다. 새 수치는
추가하지 않습니다.

## 아직 주장하지 않는 것

| 항목 | 현재 상태 | 다음 보강 |
| --- | --- | --- |
| query-count 자동 회귀 테스트 | `ProductQueryTest`가 `getAllProductsWithDetails()` 조회 SQL 1회, security filter를 제외한 `GET /api/products` 응답 변환 경로의 SQL 3회 이하, 인증 `GET /api/products` 팔로우 여부 응답 경로의 SQL 5회 이하, ranking data path SQL 5회 이하, `GET /ranking` handler/model assembly SQL 6회 이하, bulk follow lookup SQL 1회, 상품 검색/해시태그 검색 결과 DTO 접근 SQL 1회, exercise hashtag DTO 변환 SQL 1회를 guard합니다. README의 전체 Before/After 수치 201회 -> 3회를 그대로 재실행하는 자동 k6 검증은 아직 없습니다. | 실제 템플릿 렌더링 성능과 반복 k6 측정 artifact 추가 |
| EXPLAIN output | 원본 Before/After EXPLAIN 결과는 repository에 보존되어 있지 않습니다. 현재 코드 기준 EXPLAIN artifact는 `docs/evidence/explain/20260522-product-list-query/`에 보존했습니다. | 개선 전후 EXPLAIN output과 실행 환경을 함께 보존 |
| 검색 성능 p95 | README의 검색 `p95 72ms`는 원본 README 기록이며 raw k6 artifact와 실행 환경이 보존되어 있지 않습니다. 2026-05-22 현재 코드 기준 local search snapshot은 별도 artifact로 보존했습니다. | 같은 fixture와 실행 조건에서 반복 측정하고 원본 기록과 현재 snapshot을 분리 |
| Flyway baseline validation | 현재 schema baseline은 `V1__baseline_schema.sql`과 `FlywayMigrationTest`로 검증합니다. 다만 기존 production migration 이력을 모두 복원했다는 주장은 하지 않습니다. | 후속 schema 변경을 baseline 이후 migration으로 분리 |
| Testcontainers 범위 | 상품 목록 query guard, 인증 상품 목록 follow-aware API guard, ranking data path/model assembly guard, bulk follow lookup guard, 상품 검색 query guard, 해시태그 검색 query guard, exercise hashtag query guard, 예약 동시성, 재고 부족 실패 시 재고/예약 row 불변성, Flyway baseline validation은 MySQL Testcontainers로 검증합니다. 모든 기능의 MySQL 기반 회귀 테스트가 완비됐다고 주장하지는 않습니다. | 실제 템플릿 렌더링과 운영 부하 측정까지 확장 |
| 운영 성능 | 2026-05-22 product-listing/search/concurrent-reserve local snapshot은 raw artifact를 보존했지만 단일 local run입니다. 원본 Before/After 기록이나 장기 운영 성능, autoscaling 성능으로 확장하지 않습니다. | 실행 환경, dataset, 명령어, 원본 결과 파일을 함께 보존한 반복 측정 |

## 유지할 원칙

- 팀 프로젝트 전체를 혼자 설계/구현했다고 표현하지 않습니다.
- 측정 환경이 확인되지 않은 CPU, memory, DB 버전은 추가하지 않습니다.
- 새 benchmark를 실행할 때는 raw output, fixture, 명령어, git 상태를 함께 보존하고 원본 README 기록과
  현재 snapshot을 분리해 설명합니다.
- schema 변경은 baseline과 후속 migration을 분리해 기록하고, baseline validation을 production
  migration history 복원처럼 표현하지 않습니다.
