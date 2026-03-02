# k6 부하 테스트

BorrowMe API의 성능 최적화 효과를 검증하는 k6 부하 테스트입니다.

## 사전 준비

```bash
# k6 설치
brew install k6

# MySQL에 테스트 데이터 시딩
mysql -u root -p shop < k6/setup-data.sql
```

## 테스트 실행

앱이 실행 중인 상태에서 (`./gradlew bootRun`):

```bash
# 1. 동시 예약 테스트 (Pessimistic Lock 검증)
k6 run k6/test-concurrent-reserve.js

# 2. 상품 목록 조회 테스트 (N+1 최적화 검증)
k6 run k6/test-product-listing.js

# 3. 검색 테스트
k6 run k6/test-search.js
```

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
UPDATE videos SET available_quantity = 50, reservation_status = 'AVAILABLE'
WHERE title = 'k6_test_concurrent_reserve';

DELETE FROM reservations WHERE video_id = (
  SELECT id FROM videos WHERE title = 'k6_test_concurrent_reserve'
);
```
