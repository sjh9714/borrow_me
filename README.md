# BorrowMe

물건 대여 플랫폼 — 상품 등록, 예약, 팔로우, 댓글, 랭킹, 알림까지 갖춘 Spring Boot REST API

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6-6DB33F?logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Authentication-000000?logo=jsonwebtokens&logoColor=white)
![AWS S3](https://img.shields.io/badge/AWS-S3-232F3E?logo=amazonaws&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?logo=swagger&logoColor=black)

---

## 아키텍처

### 레이어드 아키텍처

```
Client (React)
    │
    ▼
┌──────────────────────────────────────────────┐
│  Controller Layer  (13개 REST Controller)     │
│  - 요청/응답 처리, 입력 검증 (@Valid)          │
│  - 인증/인가 확인                              │
├──────────────────────────────────────────────┤
│  Service Layer  (22개 Service)                │
│  - 비즈니스 로직, @Transactional 관리          │
│  - 읽기 전용 메서드: @Transactional(readOnly)  │
├──────────────────────────────────────────────┤
│  Repository Layer  (14개 JPA Repository)      │
│  - JOIN FETCH, 배치 쿼리, Pessimistic Lock    │
├──────────────────────────────────────────────┤
│  Entity Layer  (16개 Domain Model)            │
│  - JPA 매핑, 연관관계, 상태 관리               │
└──────────────────────────────────────────────┘
         │                          │
    ┌────┘                          └────┐
    ▼                                    ▼
 MySQL 8.0                          AWS S3
 (상품, 사용자, 예약 등)              (상품 이미지)
```

### JWT 인증 흐름

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter
    │ Authorization: Bearer <token>
    ▼
JwtTokenProvider.validateToken()
    │
    ├─ 유효 → SecurityContext에 Authentication 설정 → Controller 진입
    │
    └─ 만료/무효 → 401 Unauthorized
```

### 주요 엔티티 관계

```
User ──< Video (상품)
 │          │
 │          ├──< Reservation (예약)
 │          ├──< Comment ──< Reply (답글)
 │          ├──< Like
 │          └──<> Hashtag (M:N)
 │
 ├──< Follow (팔로워/팔로잉)
 └──< Notification (알림)
```

---

## 주요 기능

| 기능 | 설명 | API |
|------|------|-----|
| **상품 관리** | CRUD + S3 이미지 업로드 + 해시태그 태깅 | `POST/GET/PUT/DELETE /api/products` |
| **예약 시스템** | 수량 기반 예약/취소 + Pessimistic Lock 재고 관리 | `POST /api/products/{id}/reserve` |
| **JWT 인증** | Stateless 인증, 토큰 갱신, BCrypt 암호화 | `POST /api/auth/login`, `/register` |
| **소셜 기능** | 팔로우/언팔로우, 좋아요, 댓글, 답글 | `/api/follows`, `/api/likes`, `/api/comments` |
| **랭킹** | 팔로워 수 기반 Top 10 사용자 + 최근 상품 | `GET /ranking` |
| **검색** | 상품명/설명/사용자명 + 운동/해시태그 통합 검색 | `GET /api/search` |
| **알림** | 댓글/답글/팔로우 실시간 알림 + 읽음 처리 | `/api/notifications` |
| **이메일 인증** | 회원가입 시 이메일 인증 코드 발송 | Gmail SMTP |

---

## 문제 해결

### 1. N+1 쿼리 → JOIN FETCH + 배치 쿼리

**문제**: 상품 목록 조회 시 상품 N개마다 User, Hashtag, Follow 쿼리가 개별 실행

```java
// Before: 상품마다 개별 팔로우 조회 → 1 + N + N 쿼리
List<Video> videos = videoService.getAllVideos();
videos.stream().map(video -> {
    boolean isFollowed = followService.isFollowing(currentUser, video.getUser()); // 쿼리 N번
    return convertToResponse(video, isFollowed);
});
```

**해결**: JOIN FETCH로 연관 데이터를 한 번에 조회하고, 팔로우 상태를 배치 쿼리로 사전 로딩

```java
// After: JOIN FETCH + 배치 쿼리 → 1 + 1 + 1 쿼리
List<Video> videos = videoService.getAllVideosWithDetails(); // JOIN FETCH 1회

Set<Long> followedUserIds = followService.getFollowedUserIds(
    currentUser, videoOwners);                               // 배치 쿼리 1회

videos.stream().map(video ->
    convertToProductResponse(video, followedUserIds));        // Set.contains() O(1)
```

```java
// Repository: JOIN FETCH 쿼리
@Query("SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.user LEFT JOIN FETCH v.hashtags")
List<Video> findAllWithUserAndHashtags();

// Repository: 배치 팔로우 조회
List<Follow> findByFollowerAndFollowedIn(User follower, List<User> followed);
```

**결과**: 상품 100개 기준, **201회 → 3회** 쿼리로 감소

---

### 2. 동시성 문제 → Pessimistic Lock

**문제**: 여러 사용자가 동시에 같은 상품을 예약하면 재고가 음수가 될 수 있음 (Race Condition)

```
시간 ──────────────────────────────────────────>

트랜잭션 A:  READ(재고=1)  ──────────  UPDATE(재고=0)  COMMIT
트랜잭션 B:       READ(재고=1)  ──  UPDATE(재고=0)  COMMIT

결과: 재고 1개인 상품에 2건 예약 성공 (데이터 불일치)
```

**해결**: `@Lock(PESSIMISTIC_WRITE)`로 행 레벨 잠금 적용

```java
// Repository: SELECT ... FOR UPDATE
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT v FROM Video v WHERE v.id = :id")
Optional<Video> findByIdForUpdate(Long id);
```

**추가 발견 — Hibernate L1 캐시가 Pessimistic Lock을 무력화하는 문제**

k6 부하 테스트(100명 동시 예약)로 검증하는 과정에서, `SELECT FOR UPDATE`가 실제로 실행되지 않는 현상을 발견.

- **원인**: Spring Boot의 OSIV(`open-in-view=true`)가 기본 활성화되어 Controller부터 같은 Hibernate Session이 유지됨. Controller에서 `getVideoById()`로 이미 로딩된 Video 엔티티가 L1 캐시에 존재하므로, Service에서 `findByIdForUpdate()`를 호출해도 **DB 조회 없이 캐시된 엔티티를 반환** → `FOR UPDATE` 잠금이 걸리지 않음
- **결과**: 재고 50개 상품에 100명이 동시 예약 → **100건 전부 성공**, 재고 불일치 발생

```java
// Before: L1 캐시로 인해 SELECT FOR UPDATE가 실행되지 않음
@Transactional
public Reservation reserve(Video video, User user, int quantity) {
    // video가 이미 L1 캐시에 존재 → DB 조회 없이 캐시 반환
    Video lockedVideo = videoRepository.findByIdForUpdate(video.getId()); // FOR UPDATE 무시됨
    // ...
}
```

```java
// After: entityManager.detach()로 L1 캐시 제거 후 SELECT FOR UPDATE 실행
@Transactional
public Reservation reserve(Video video, User user, int quantity) {
    entityManager.detach(video); // L1 캐시에서 제거

    Video lockedVideo = videoRepository.findByIdForUpdate(video.getId()) // FOR UPDATE 정상 실행
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

    if (lockedVideo.getAvailableQuantity() < quantity) {
        throw new IllegalStateException("재고가 부족합니다.");
    }

    lockedVideo.setAvailableQuantity(lockedVideo.getAvailableQuantity() - quantity);
    // ...
}
```

```
시간 ──────────────────────────────────────────>

트랜잭션 A:  detach → LOCK ── READ(재고=1) ── UPDATE(재고=0) ── COMMIT ── UNLOCK
트랜잭션 B:           (대기)                                      LOCK ── READ(재고=0) ── 예외 발생

결과: 정확히 1건만 예약 성공, 재고 정합성 보장
```

**k6 검증 결과**: 재고 50개 상품 + 100명 동시 예약 → 성공 50건 + 실패 50건, 최종 재고 0 (정합성 100%)

---

### 3. 민감 정보 노출 방지

**문제**: User 엔티티가 Comment, Like 등의 연관 관계를 통해 API 응답에 포함될 때 `passwordHash`, `verificationToken`이 함께 직렬화됨

```json
// Before: API 응답에 비밀번호 해시가 노출
{
  "comment": "좋은 상품이네요",
  "user": {
    "username": "john",
    "passwordHash": "$2a$10$...",          // 노출
    "verificationToken": "abc123..."       // 노출
  }
}
```

**해결**: `@JsonIgnore`로 민감 필드 제외, `@JsonIgnoreProperties`로 순환 참조 방지

```java
@JsonIgnoreProperties({"videos", "comments", "likes", "following",
    "followers", "hibernateLazyInitializer"})
public class User implements Serializable {

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String verificationToken;

    @JsonIgnore
    private LocalDateTime verificationTokenExpiry;
}
```

추가 조치:
- `application.properties`의 DB/JWT/OAuth 비밀키 → 환경변수로 분리
- `git filter-repo`로 git 이력에서 노출된 비밀키 완전 제거
- GitHub Push Protection 활용하여 재노출 방지

---

### 4. 해시태그 N+1 Upsert → 배치 패턴

**문제**: 해시태그 N개를 저장할 때 각각 `findByName` + `save`로 2N회 쿼리 실행

```java
// Before: 개별 조회 + 개별 저장 → 2N 쿼리
for (String name : hashtagNames) {
    Hashtag hashtag = hashtagRepository.findByName(name)  // 쿼리 N번
            .orElseGet(() -> {
                Hashtag h = new Hashtag();
                h.setName(name);
                return hashtagRepository.save(h);          // 쿼리 N번
            });
    result.add(hashtag);
}
```

**해결**: `findByNameIn`으로 일괄 조회, 누락분만 `saveAll`로 일괄 저장

```java
// After: 배치 조회 + 배치 저장 → 최대 2 쿼리
List<Hashtag> existing = hashtagRepository.findByNameIn(hashtagNames);  // 1회
Map<String, Hashtag> existingMap = existing.stream()
        .collect(Collectors.toMap(Hashtag::getName, h -> h));

List<Hashtag> newHashtags = hashtagNames.stream()
        .filter(name -> !existingMap.containsKey(name))
        .map(name -> { Hashtag h = new Hashtag(); h.setName(name); return h; })
        .collect(Collectors.toList());

if (!newHashtags.isEmpty()) {
    result.addAll(hashtagRepository.saveAll(newHashtags));              // 1회
}
```

---

## 성능 최적화

### 쿼리 최적화 요약

| 위치 | Before | After | 개선 |
|------|--------|-------|------|
| `ProductController.getProducts()` | 1 + N + N 쿼리 | 1 + 1 + 1 쿼리 | 상품 수 무관 고정 쿼리 |
| `RankingController.showRanking()` | 1 + N + N 쿼리 | 1 + 1 + 1 쿼리 | 유저 수 무관 고정 쿼리 |
| `VideoServiceImpl` (해시태그 upsert) | 2N 쿼리 | 최대 2 쿼리 | 해시태그 수 무관 |
| `VideoServiceImpl` (댓글 목록) | `.size()` 강제 로딩 | JOIN FETCH | 추가 쿼리 제거 |

### @Transactional 최적화

읽기 전용 메서드 12개에 `@Transactional(readOnly = true)` 적용:

- **효과**: Hibernate Dirty Checking 비활성화 → 플러시 생략 → 메모리/CPU 절약
- **적용**: `UserServiceImpl`(7개), `ExerciseServiceImpl`(2개), `ReservationServiceImpl`(3개)

### 동시성 제어

| 전략 | 적용 위치 | 보호 대상 |
|------|----------|----------|
| Pessimistic Lock (`SELECT FOR UPDATE`) | `ReservationServiceImpl.reserve()` | 상품 재고 수량 |
| `@Transactional` 격리 | 예약 생성/취소 | 재고 + 예약 상태 일관성 |
| 자동 상태 전이 | 재고 변경 시 | `AVAILABLE → RESERVED → OUT_OF_STOCK` |
| L1 캐시 무력화 방지 | `entityManager.detach()` | OSIV 환경에서 `FOR UPDATE` 정상 작동 보장 |

### k6 부하 테스트 결과

k6로 실제 부하 환경에서 성능 최적화 효과를 수치로 검증:

| 테스트 | VU | 조건 | p95 응답시간 | 처리량 | 결과 |
|--------|-----|------|-------------|--------|------|
| **동시 예약** | 100 | 재고 50개, 1회씩 동시 요청 | 233ms | - | 성공 50건, 실패 50건, 초과 예약 0건 |
| **상품 목록 조회** | 30 | 상품 100개 + 해시태그 + 팔로우, 30초 지속 | 20ms | 256 req/s | 에러율 0%, 7,761회 처리 |
| **검색** | 30 | 다양한 검색어, 30초 지속 | 72ms | 223 req/s | 에러율 0%, 6,754회 처리 |

- **동시 예약**: Pessimistic Lock + `entityManager.detach()`로 100명 동시 접근에서도 재고 정합성 100% 보장
- **상품 목록**: JOIN FETCH + 배치 쿼리 최적화로 30 VU 지속 부하에서 p95 20ms 유지
- **검색**: 해시태그 배치 로딩으로 30 VU 지속 부하에서 p95 72ms, 에러율 0%

---

## 프로젝트 구조

```
src/main/java/com/ardkyer/rion/
├── controller/          # REST API 엔드포인트 (13개)
│   ├── ProductController        - 상품 CRUD + 예약
│   ├── UserController           - 회원가입, 로그인
│   ├── FollowController         - 팔로우/언팔로우
│   ├── CommentController        - 댓글 CRUD
│   ├── ReplyController          - 답글
│   ├── LikeController           - 좋아요
│   ├── NotificationController   - 알림
│   ├── SearchController         - 검색
│   ├── RankingController        - 랭킹
│   └── ...
├── service/             # 비즈니스 로직 (22개)
├── repository/          # 데이터 접근 (14개)
├── entity/              # 도메인 모델 (16개)
│   ├── User, Video, Reservation, Comment, Reply
│   ├── Follow, Like, CommentLike, Hashtag
│   ├── Notification, Exercise, ItemUnit
│   └── EmailVerification, RecentSearch
├── config/              # 설정 (7개)
│   ├── SecurityConfig           - Spring Security + JWT
│   ├── GlobalExceptionHandler   - 전역 예외 처리
│   ├── EmailConfig              - Gmail SMTP
│   ├── S3Config                 - AWS S3
│   └── OpenApiConfig            - Swagger
├── security/            # JWT 인증 (3개)
│   ├── JwtTokenProvider         - 토큰 생성/검증
│   ├── JwtAuthenticationFilter  - 요청 필터
│   └── PrincipalDetails         - 사용자 인증 정보
└── dto/                 # 요청/응답 DTO
    ├── request/         - LoginRequest, SignupRequest (Bean Validation)
    └── response/        - UserResponse, LoginResponse, ProductResponse
```

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Backend** | Spring Boot 3.1.5, Java 17 |
| **Database** | MySQL 8.0 (운영), H2 (테스트) |
| **ORM** | Spring Data JPA, Hibernate |
| **Security** | Spring Security 6, JWT (jjwt 0.11.5), BCrypt |
| **Storage** | AWS S3 (Spring Cloud AWS) |
| **Email** | Spring Mail (Gmail SMTP) |
| **Documentation** | SpringDoc OpenAPI 3 (Swagger UI) |
| **Validation** | Jakarta Bean Validation |
| **Build** | Gradle |
| **Test** | JUnit 5, H2 인메모리 DB |
| **Load Test** | k6 (동시성 검증, 부하 테스트) |

---

## 실행 방법

### 환경변수 설정

```bash
export DB_PASSWORD=your_db_password
export JWT_SECRET=your_jwt_secret_key_minimum_256_bits
export AWS_ACCESS_KEY_ID=your_aws_access_key
export AWS_SECRET_ACCESS_KEY=your_aws_secret_key
export MAIL_USERNAME=your_email@gmail.com
export MAIL_PASSWORD=your_app_password
```

### 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트 (H2 인메모리 DB 사용)
./gradlew test
```

### API 문서

서버 실행 후 Swagger UI에서 전체 API 확인:

```
http://localhost:5000/swagger-ui/index.html
```
