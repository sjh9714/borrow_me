# k6 부하 테스트

BorrowMe API의 성능 최적화 효과를 재확인하기 위한 k6 재실행용 시나리오입니다. 과거 README의 상품
목록/검색 raw artifact는 보존되어 있지 않으므로, 새 결과를 주장하려면 실행 로그를 함께 남겨야 합니다.

## 사전 준비

```bash
# k6 설치
brew install k6
```

로컬 MySQL이 없다면 k6 재실행용 MySQL만 Docker로 띄울 수 있습니다. 이 compose는 DB만 제공하며,
애플리케이션 실행이나 성능 수치 생성을 대신하지 않습니다.

```bash
docker compose -f docker-compose.k6.yml up -d

# MySQL healthcheck 확인
docker compose -f docker-compose.k6.yml ps
```

현재 `local` profile은 `localhost:3306/shop`을 사용합니다. 3306 포트를 이미 다른 MySQL이 쓰고 있다면
기존 MySQL에 직접 시딩하거나, 충돌하는 서비스를 먼저 정리한 뒤 compose를 사용합니다.

애플리케이션 부팅에 필요한 로컬 전용 dummy 환경 변수는 예시 파일을 source 해서 주입합니다.

```bash
source k6/local-k6.env.example
```

처음 실행하는 DB라면 앱을 한 번 부팅해 Hibernate가 테이블을 만든 뒤 테스트 데이터를 시딩합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'

# 별도 터미널에서 앱이 뜬 뒤 실행
mysql -h 127.0.0.1 -P 3306 -u "$DB_USERNAME" -p"$DB_PASSWORD" shop < k6/setup-data.sql
```

## 테스트 실행

앱은 local profile과 필요한 환경 변수를 넣어 실행합니다. 예:

```bash
source k6/local-k6.env.example
./gradlew bootRun --args='--spring.profiles.active=local'
```

앱이 실행 중인 상태에서 k6를 실행합니다. 기본 `BASE_URL`은 `http://localhost:5000`입니다.
현재 보존된 2026-05-22 local snapshot은 해당 실행 시점에 비어 있던 `http://localhost:5001`에서
실행한 결과입니다.

```bash
# 1. 동시 예약 테스트 (Pessimistic Lock 검증)
BASE_URL=http://localhost:5000 k6 run k6/test-concurrent-reserve.js

# 2. 상품 목록 조회 재실행 시나리오
BASE_URL=http://localhost:5000 k6 run k6/test-product-listing.js

# 3. 검색 테스트
BASE_URL=http://localhost:5000 k6 run k6/test-search.js
```

## Raw evidence 보존 규칙

새 성능 수치를 README나 포트폴리오에 반영하려면 k6 summary와 콘솔 로그를 함께 저장합니다. 아래 명령은
새 benchmark 결과를 주장하는 명령이 아니라, 실행할 때의 artifact 보존 방식입니다.

```bash
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh product-listing
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh search
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh concurrent-reserve
```

`k6/run-with-evidence.sh`는 `docs/evidence/k6/<timestamp>-<scenario>/` 아래에 `summary.json`,
`console.txt`, `metadata.txt`를 함께 저장합니다. metadata에는 UTC 실행 시각, git commit/status,
k6/JVM/OS 정보, `BASE_URL`, 실행 명령, dataset 전제 조건과 가능한 경우 `k6/setup-data.sql` checksum을
남깁니다.

이 artifact가 없으면 기존 README 수치는 계속 `원본 README 기록 기준`으로만 설명합니다.

현재 보존된 local snapshot:

| 시나리오 | raw artifact | 해석 |
| --- | --- | --- |
| product-listing | `docs/evidence/k6/20260522T070732Z-product-listing/` | 해당 실행 시점의 worktree와 fixture 기준 단일 local snapshot |
| search | `docs/evidence/k6/20260522T073727Z-search/` | recent search upsert 보강 후 해당 실행 시점의 worktree와 fixture 기준 단일 local snapshot |
| concurrent-reserve | `docs/evidence/k6/20260522T074050Z-concurrent-reserve/` | 재고 50개 / 100 VU 정합성 단일 local snapshot |

## 테스트 시나리오

| 테스트 | VU | 설정 | 검증 대상 |
|--------|-----|------|-----------|
| 동시 예약 | 100 | 재고 50개 상품에 동시 예약 | Pessimistic Lock 정합성 |
| 상품 목록 | 30 | 상품 100개, 30초 지속 | JOIN FETCH + 배치 쿼리 |
| 검색 | 30 | 다양한 검색어, 30초 지속 | 검색 성능 |

## 테스트 데이터

`setup-data.sql`이 생성하는 데이터:
- 사용자 102명 (소유자 2명 + 테스터 100명)
- 상품 101개 (일반 100개 + 동시 예약용 1개)
- 해시태그 20개 + 상품-해시태그 관계
- 팔로우 관계 60건

비밀번호: `TestPass123` (BCrypt 해시 저장)

## 재실행

동시 예약 테스트를 다시 실행하려면 재고를 복구해야 합니다:

```sql
UPDATE products SET available_quantity = 50, reservation_status = 'AVAILABLE'
WHERE title = 'k6_test_concurrent_reserve';

DELETE FROM reservations WHERE product_id = (
  SELECT id FROM products WHERE title = 'k6_test_concurrent_reserve'
);
```
