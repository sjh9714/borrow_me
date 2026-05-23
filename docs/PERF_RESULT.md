# Performance Result

이 문서는 BorrowMe의 성능 관련 기록을 한 곳에서 찾기 위한 index입니다. 원본 README 수치와 현재
로컬 재실행 snapshot을 분리해서 해석합니다.

## 측정 / 기록 상태

| 항목 | 상태 | 문서 |
| --- | --- | --- |
| 상품 목록 p95 / 처리량 / 쿼리 수 | 원본 README 기록 기준 + 2026-05-23 clean repeat3 로컬 재실행 snapshot + 2026-05-22 이전 snapshot | [PRODUCT_LIST_PERF.md](PRODUCT_LIST_PERF.md) |
| 검색 p95 | 원본 README 72ms 기록과 별개로 2026-05-22 로컬 재실행 snapshot 보존 | [LIMITATIONS.md](LIMITATIONS.md) |
| 동시 예약 재고 정합성 | Testcontainers 시나리오 검증 + 2026-05-22 로컬 k6 재실행 snapshot | [RESERVATION_CONSISTENCY.md](RESERVATION_CONSISTENCY.md) |

## 해석 원칙

- 상품 목록 p95 1,010ms -> 23ms와 처리량 30 req/s -> 253 req/s는 원본 README 기록입니다.
- 2026-05-23 product-listing clean repeat3 k6 재실행은 clean commit과 `k6/setup-data.sql` fixture에서의 current remeasurement snapshot입니다.
- 2026-05-22 k6 재실행은 해당 실행 시점의 worktree와 `k6/setup-data.sql` fixture에서의 local snapshot입니다.
  과거 Before/After p95 23ms나 검색 p95 72ms를 같은 조건으로 재현했다는 뜻이 아니며, 운영 성능 claim으로 확장하지 않습니다.
- 현재 repo에서 자동으로 재검증하는 핵심은 query-count guard와 reservation concurrency test입니다.
- raw k6 output, 실행 환경, dataset이 보존되지 않은 수치는 운영 성능 claim으로 확장하지 않습니다.

## 현재 로컬 재실행 snapshot

| 실행 일시 | 시나리오 | 조건 | 핵심 결과 | raw artifact |
| --- | --- | --- | --- | --- |
| 2026-05-23T00:46:42Z | `product-listing` clean repeat3 | 30 VU, 30초, local profile app on `localhost:5001`, throwaway Docker MySQL `shop` on host port 3307, clean git commit `01c255a9f1863ef8f0b80854cc159a70eee036eb` | p50 121.6ms, p95 358.1ms, p99 557.66ms, HTTP 실패율 0.00%, checks 10,683 / 10,683 성공, query count는 k6 artifact에 없음 | `docs/evidence/k6/20260523T004642Z-product-listing/` |
| 2026-05-22T07:07:32Z | `product-listing` | 30 VU, 30초, local profile app on `localhost:5001`, Docker MySQL `shop` | p95 50.5ms, HTTP 실패율 0.00%, checks 20,985 / 20,985 성공 | `docs/evidence/k6/20260522T070732Z-product-listing/` |
| 2026-05-22T07:37:27Z | `search` | 30 VU, 30초, local profile app on `localhost:5001`, Docker MySQL `shop` | p95 133.03ms, p99 182.01ms, HTTP 실패율 0.00%, checks 10,976 / 10,976 성공 | `docs/evidence/k6/20260522T073727Z-search/` |
| 2026-05-22T07:40:50Z | `concurrent-reserve` | 100 VU, 재고 50개, local profile app on `localhost:5001`, Docker MySQL `shop` | 예약 성공 50건, 재고 부족 실패 50건, 최종 재고 0, 예상 밖 오류 0건 | `docs/evidence/k6/20260522T074050Z-concurrent-reserve/` |

이 snapshot은 현재 코드에서 각 k6 threshold를 통과함을 보여줍니다. 원본 README의 Before/After 개선 폭과
동일한 조건의 재현이라고 주장하지 않습니다.

## 재실행 후보

```bash
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh product-listing
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh search
BASE_URL=http://localhost:5000 k6/run-with-evidence.sh concurrent-reserve
```

재실행 후에는 raw output, 실행 환경, fixture 상태, git commit을 함께 보존한 뒤에만 측정 완료 수치로
갱신합니다.
