# Testing Evidence

이 문서는 BorrowMe의 포트폴리오 claim을 어떤 테스트가 지지하는지와 아직 claim하지 않는
범위를 분리합니다. 새 수치는 추가하지 않습니다.

## 검증된 범위

| 테스트 | 환경 | 검증하는 주장 |
| --- | --- | --- |
| `ProductQueryTest` | MySQL Testcontainers + Hibernate statistics | `getAllProductsWithDetails()` 조회 경로가 SQL 1회로 유지되는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + MockMvc | security filter를 제외한 `GET /api/products` 응답 변환 경로가 SQL 3회 이하로 유지되는지 guard |
| `ProductQueryTest.getProductsApi_withAuthentication_shouldBatchFollowLookup` | MySQL Testcontainers + MockMvc | 인증 사용자의 `GET /api/products` 응답에서 팔로우 여부 true/false를 포함하고 SQL 5회 이하로 유지되는지 guard |
| `ProductQueryTest.rankingDataPath_shouldKeepQueryCountBounded` | MySQL Testcontainers + Hibernate statistics | ranking data path의 상위 사용자, 최근 상품, 팔로우 여부 조회 조합이 SQL 5회 이하로 유지되는지 guard |
| `ProductQueryTest.rankingPage_shouldRenderModelAndKeepQueryCountBounded` | MySQL Testcontainers + MockMvc + test no-op view | `GET /ranking` handler/model assembly가 topUsers/currentUser/recentProducts/followed flag를 구성하고 SQL 6회 이하로 유지되는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + Hibernate statistics | `FollowService.getFollowedUserIds()`가 후보 사용자 팔로우 여부를 SQL 1회로 일괄 조회하는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + MockMvc | `GET /api/products/{id}` 단건 조회가 user/hashtag를 응답 변환 전에 로딩하는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + Hibernate statistics | 상품 검색과 해시태그 검색 결과의 DTO 접근이 SQL 1회로 유지되는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + Hibernate statistics | 운동 추천/검색 응답의 exercise hashtag DTO 변환이 SQL 1회로 유지되는지 guard |
| `ProductQueryTest` | MySQL Testcontainers + 실제 동시 thread | 같은 사용자/키워드 recent search 저장이 동시 요청에서도 한 row로 유지되는지 guard |
| `ReservationConcurrencyTest` | MySQL Testcontainers + 실제 Spring context | 재고 50개 상품에 동시 예약 100회를 시도했을 때 성공 50건, 예약 row 50건, 최종 재고 0을 검증 |
| `ReservationConcurrencyTest` | MySQL Testcontainers + 실제 Spring context | 재고 부족으로 예약이 실패하면 예약 row와 상품 재고가 바뀌지 않는지 검증 |
| `FlywayMigrationTest` | MySQL 8.0 Testcontainers + Flyway + Hibernate `ddl-auto=validate` | baseline schema migration이 핵심 테이블과 `flyway_schema_history`를 만들고 entity validation을 통과하는지 검증 |

## Testcontainers 실행 전제

이 테스트들은 외부 MySQL 인스턴스의 상태를 근거로 삼지 않습니다. Docker가 실행 중인 환경에서
Testcontainers가 테스트용 MySQL 컨테이너를 띄우고, 테스트 fixture를 만든 뒤 query-count와 예약
정합성을 확인합니다.

- 통과 의미: 현재 코드가 정해진 fixture에서 N+1 회귀 guard와 예약 정합성 시나리오를 만족합니다.
- 실패 의미: 쿼리 수 증가, lazy loading 재발, 잠금 경로 변경, fixture/schema drift를 먼저 확인합니다.
- 한계: k6 p95/처리량을 재측정하지 않으며, Flyway 검증은 baseline schema validation에 한정됩니다.

## CI 경계

`.github/workflows/ci.yml`은 push/PR에서 whitespace check, k6 script inspection, `./gradlew test`,
`./gradlew build`를 실행하도록 구성되어 있습니다. GitHub Actions 통과 여부는 badge와 run history로
확인하며, 이 문서는 remote CI 통과 수치를 새로 주장하지 않습니다.

## 아직 검증하지 않는 범위

| 범위 | 이유 |
| --- | --- |
| README의 상품 목록 k6 Before/After 원본 재실행 | 현재 테스트는 query-count 회귀 guard이며 p95/처리량 재측정은 아닙니다. |
| 인증 filter까지 포함한 end-to-end 상품 목록 성능 | 인증 `GET /api/products` 응답 변환과 팔로우 여부 query-count는 guard하지만, security filter 내부 쿼리와 HTTP 성능 수치까지 포함한 새 benchmark는 아닙니다. |
| 랭킹 HTTP 렌더링 성능 | `GET /ranking` handler/model assembly와 query-count는 guard하지만, 실제 템플릿 렌더링 시간이나 HTTP 성능 수치까지 측정한 benchmark는 아닙니다. |
| 운영 migration history 복원 | `V1__baseline_schema.sql`은 현재 schema baseline validation이며, 과거 production 변경 이력을 모두 복원했다는 주장이 아닙니다. |
| 운영 환경 성능 / autoscaling | 로컬/테스트 환경의 claim을 운영 성능으로 확장하지 않습니다. |

## 실행 명령

```bash
./gradlew test --tests ProductQueryTest --no-daemon
./gradlew test --tests ReservationConcurrencyTest --no-daemon
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests com.ardkyer.borrowme.FlywayMigrationTest --no-daemon
./gradlew test --no-daemon
./gradlew build --no-daemon
```

## 해석 원칙

- 테스트가 통과해도 새 성능 수치를 만들었다고 표현하지 않습니다.
- `ProductQueryTest`는 N+1 회귀 방지용 guard이며, README의 p95/처리량 수치 자체를 재측정하지 않습니다.
- `ReservationConcurrencyTest`는 예약 정합성 시나리오 검증이며, 장기 운영 부하나 autoscaling 성능 주장이 아닙니다.
- `docs/evidence/k6/20260522T073727Z-search/`와 `docs/evidence/k6/20260522T074050Z-concurrent-reserve/`는 현재 local snapshot이며, 원본 README 기록과 같은 조건의 재현이라고 주장하지 않습니다.
