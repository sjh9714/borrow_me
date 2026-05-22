# 예약 정합성

BorrowMe의 예약 기능은 수량이 있는 상품에 여러 사용자가 동시에 예약을 시도할 때 재고 초과
예약이 발생하지 않아야 합니다. 이 문서는 README의 동시 예약 수치와 현재 Testcontainers/k6 snapshot을
분리해 정리합니다.

## 문제

재고 50개인 상품에 100 VU가 동시에 예약을 시도하면, 단순 읽기-수정-쓰기 방식에서는 여러
요청이 같은 재고 값을 읽고 모두 성공할 수 있습니다.

## Before

아래 Before/After는 원본 README 기록 기준이며, 당시 raw k6 artifact는 보존되어 있지 않습니다.

| 항목 | 값 |
| --- | --- |
| 조건 | 동시 예약 100 VU, 재고 50개 |
| 예약 성공 | 100건 |
| 최종 재고 | 불일치 |

## 해결

- `@Lock(PESSIMISTIC_WRITE)`를 사용해 재고를 갱신하는 row를 잠급니다.
- DB에서는 `SELECT FOR UPDATE`로 같은 상품 재고 변경을 직렬화합니다.
- Hibernate 1차 캐시가 잠금 이후 최신 재고 확인을 흐리지 않도록 `entityManager.detach()`를 함께 사용했습니다.

## After

| 항목 | 값 |
| --- | --- |
| 조건 | 동시 예약 100 VU, 재고 50개 |
| 예약 성공 | 50건 |
| 최종 재고 | 0 |
| 정합성 | 재고 초과 예약 방지 |
| 재고 부족 실패 | 예약 row 0건, 재고 50개 유지 |

## 현재 로컬 k6 snapshot

2026-05-22에 해당 실행 시점의 worktree와 `k6/setup-data.sql` fixture로 `concurrent-reserve`를 재실행했습니다.

| 항목 | 값 |
| --- | --- |
| 조건 | 100 VU, 재고 50개 |
| 예약 성공 | 50건 |
| 재고 부족 실패 | 50건 |
| 최종 재고 | 0 |
| 예상 밖 오류 | 0건 |
| p95 응답 시간 | 347.08ms |
| raw artifact | `docs/evidence/k6/20260522T074050Z-concurrent-reserve/` |

## 설계 판단

| 판단 | 이유 |
| --- | --- |
| 비관적 락 우선 | 해커톤 이후 포트폴리오 보강에서는 처리량보다 재고 정합성이 더 중요했습니다. |
| 재고 감소와 예약 생성 같은 transaction | 예약 성공과 재고 감소가 분리되면 부분 실패를 설명하기 어렵습니다. |
| 실패 요청 불변성 | 재고보다 큰 수량을 예약하려는 요청은 예약 row를 만들지 않고 재고를 바꾸지 않아야 합니다. |
| 1차 캐시 주의 | 같은 persistence context의 오래된 entity 상태가 재고 확인을 흐릴 수 있습니다. |

## 측정 한계

- 동시 예약 Before/After 수치는 README에 남은 k6 기록 기준입니다.
- 현재 자동 검증은 `ReservationConcurrencyTest`의 Testcontainers 시나리오 검증이고, 2026-05-22 local k6 snapshot은 해당 실행 시점의 worktree/fixture 단일 재실행 근거입니다.
- k6 artifact metadata에는 당시 `git status`가 포함되어 있으며, clean commit 기준 반복 측정은 추가 측정 예정입니다.
- CPU, memory, DB 버전 등 실행 환경 세부값은 현재 확인되지 않아 추가하지 않습니다.
- 운영 환경 성능이나 autoscaling을 주장하지 않습니다.

## 면접에서 설명할 질문

| 질문 | 답변 포인트 |
| --- | --- |
| 왜 비관적 락을 선택했나요? | 재고 초과 예약 방지와 결과 설명 가능성을 우선했습니다. |
| `SELECT FOR UPDATE`는 언제 SQL로 나가나요? | transaction 안에서 잠금 조회가 실행될 때 row lock을 획득합니다. |
| `entityManager.detach()`는 왜 필요했나요? | 같은 persistence context의 캐시된 상태가 최신 잠금 조회 결과와 섞이지 않게 했습니다. |
| 실패한 50건은 어떻게 처리하나요? | 재고 부족 실패로 반환하고 예약 row를 만들지 않는 방향입니다. |
