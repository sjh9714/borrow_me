# Runbook

이 문서는 BorrowMe를 포트폴리오 증거 저장소로 설명할 때, 문제 상황별로 어떤
근거를 확인할지 정리합니다. 실제 운영 장애 대응 체계를 구축했다는 주장은 하지
않습니다.

## 1. 상품 목록 조회 성능 회귀

증상:

- 상품 목록 API 응답 시간이 다시 증가합니다.
- 상품 목록 조회 쿼리 수가 증가합니다.

확인할 것:

- `ProductService#getAllProductsWithDetails()`가 `ProductRepository#findAllWithUserAndHashtags()`를
  통해 필요한 fetch join을 유지하는지 확인합니다.
- `ProductQueryTest`의 repository query-count guard가 SQL 1회 이하를 유지하는지 확인합니다.
- `ProductQueryTest`의 `GET /api/products` API 변환 경로가 security filter 쿼리를 제외하고 SQL 3회 이하를 유지하는지 확인합니다.
- 원본 README의 p95 / throughput 수치는 raw k6 artifact가 없으므로, 새로 주장하려면 실행 환경과 k6 결과 파일을 함께 보존합니다.

검증 명령:

```bash
./gradlew test --tests ProductQueryTest --no-daemon
```

## 2. 예약 재고 불일치

증상:

- 동시 예약 후 최종 재고가 음수가 되거나 재고보다 많은 예약 row가 생성됩니다.
- pessimistic lock이 적용된 조회 경로가 우회됩니다.

확인할 것:

- `ProductRepository#findByIdForUpdate()`가 `PESSIMISTIC_WRITE`를 유지하는지 확인합니다.
- 예약 처리에서 기존 영속성 컨텍스트의 상품 entity를 detach한 뒤 lock query를 다시 수행하는지 확인합니다.
- `ReservationConcurrencyTest`가 성공 50건, 실패 50건, 예약 row 50건, 최종 재고 0을 유지하는지 확인합니다.
- 현재 local k6 snapshot은 `docs/evidence/k6/20260522T074050Z-concurrent-reserve/`에 보존되어 있습니다.

검증 명령:

```bash
./gradlew test --tests ReservationConcurrencyTest --no-daemon
```

## 3. 검색 성능 claim 확인

증상:

- README의 검색 p95 수치를 면접 또는 포트폴리오에서 설명해야 합니다.
- 검색 부하 중 같은 사용자/키워드의 recent search row가 중복되어 500 응답이 발생합니다.

확인할 것:

- README의 검색 `p95 72ms`는 원본 README에 남아 있던 기록으로만 설명합니다.
- 현재 코드 기준 local search snapshot은 `docs/evidence/k6/20260522T073727Z-search/`에 보존되어 있습니다.
- `recent_search`는 `(user_id, keyword)` unique constraint와 MySQL upsert를 유지해야 같은 검색어 동시 요청에서 중복 row가 생기지 않습니다.
- `ProductQueryTest`의 recent search concurrency guard가 통과하는지 확인합니다.
- 새 측정 결과로 올리려면 실행 날짜, 하드웨어, DB 환경, dataset, command, raw output을 함께 보존합니다.

검증 명령:

```bash
./gradlew test --tests ProductQueryTest --no-daemon
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh search
```

현재 snapshot은 원본 검색 p95 72ms를 대체하지 않습니다. 같은 조건으로 반복 측정하기 전까지 운영 성능 claim으로 확장하지 않습니다.

## 4. 주장 경계

- 팀 프로젝트 전체를 혼자 설계/구현했다고 표현하지 않습니다.
- Flyway는 현재 schema baseline validation으로만 설명하고, 기존 production migration 이력을 복원했다고 주장하지 않습니다.
- 현재 자동화된 회귀 근거는 query-count guard, reservation concurrency Testcontainers 테스트, Flyway baseline validation입니다.
- p95 / throughput 수치는 원본 README 기록이며, 새 실행 환경이 보존되기 전까지 운영 성능 claim으로 확장하지 않습니다.
