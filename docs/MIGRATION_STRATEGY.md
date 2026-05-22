# Migration Strategy

BorrowMe는 기존 운영 migration 이력을 모두 복원했다고 주장하지 않습니다. 현재 범위는
엔티티 기준 baseline schema를 Flyway migration으로 정리하고, MySQL Testcontainers에서
`ddl-auto=validate`로 schema drift를 잡는 검증입니다.

## 현재 상태

| 항목 | 현재 기준 |
| --- | --- |
| 개발/로컬 profile | Hibernate `ddl-auto=update`와 MySQL `shop` 데이터베이스 |
| 기본 테스트 profile | H2/create-drop 또는 MySQL Testcontainers fixture, Flyway는 기본 비활성 |
| Flyway baseline validation | `V1__baseline_schema.sql` + `FlywayMigrationTest` |
| 운영 profile | `ddl-auto=validate`로 외부 DB schema가 entity와 맞는지 확인 |
| k6 fixture | `k6/setup-data.sql`로 성능 테스트용 데이터셋 구성 |

현재 포트폴리오에서 검증하는 핵심은 상품 목록 query-count guard, 예약 정합성
Testcontainers 시나리오, 그리고 baseline schema migration이 핵심 테이블과
`flyway_schema_history`를 생성하는지입니다.

## Baseline validation

`src/main/resources/db/migration/V1__baseline_schema.sql`은 현재 엔티티와 테스트에서
필요한 schema를 재현하는 baseline입니다. `FlywayMigrationTest`는 MySQL 8.0
Testcontainers에서 Flyway를 활성화하고, Hibernate `ddl-auto=validate`와 함께 다음을
확인합니다.

- `flyway_schema_history`가 생성되고 migration이 성공 상태로 기록됩니다.
- `user`, `products`, `reservations`, `product_hashtags`, `recent_search` 같은 핵심
  테이블이 생성됩니다.
- Flyway migration 이후 Hibernate schema validation이 통과합니다.

실행 명령:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests com.ardkyer.borrowme.FlywayMigrationTest --no-daemon
```

## 왜 baseline과 history를 분리해서 설명하는가

- 해커톤 팀 프로젝트 이후 포트폴리오 보강 과정에서 기존 schema의 실제 변경 이력을 완전히
  보존하지 못했습니다.
- `V1__baseline_schema.sql`은 현재 schema를 재현하는 baseline이지, 과거 production에
  순차 적용된 migration history가 아닙니다.
- 따라서 “Flyway로 운영 migration 이력을 관리했다”가 아니라 “baseline migration
  validation을 자동화했다”로만 설명합니다.

## 후속 migration 후보

| 후보 | 이유 |
| --- | --- |
| `recent_search(user_id, keyword)` unique constraint 후속 분리 | 같은 사용자/키워드 동시 검색에서 중복 row를 막는 정책을 별도 변경 이력으로 관리 |
| Product 조회 관련 index | 상품 목록/검색 조회 경로의 성능 회귀를 줄이기 위한 후보 |
| Reservation 관련 index | 상품별 예약 상태 확인과 동시 예약 검증 경로의 후보 |

## 아직 주장하지 않는 것

- Flyway migration이 production에 적용되어 운영 이력을 관리했다는 주장.
- 기존 schema 변경 이력을 모두 복원했다는 주장.
- 모든 후속 schema 변경이 migration 파일로 관리된다는 주장.
- index 추가가 새 p95/throughput 개선 수치를 만들었다는 주장.
