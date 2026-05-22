package com.ardkyer.borrowme;

import com.ardkyer.borrowme.entity.Hashtag;
import com.ardkyer.borrowme.entity.Follow;
import com.ardkyer.borrowme.entity.Exercise;
import com.ardkyer.borrowme.entity.Product;
import com.ardkyer.borrowme.entity.User;
import com.ardkyer.borrowme.repository.ProductRepository;
import com.ardkyer.borrowme.service.ExerciseService;
import com.ardkyer.borrowme.service.FollowService;
import com.ardkyer.borrowme.service.ProductService;
import com.ardkyer.borrowme.service.RecentSearchService;
import com.ardkyer.borrowme.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@ActiveProfiles("test")
class ProductQueryTest {

    @TestConfiguration
    static class NoOpViewConfiguration {
        @Bean
        ViewResolver noOpViewResolver() {
            return (viewName, locale) -> new AbstractView() {
                @Override
                protected void renderMergedOutputModel(
                        Map<String, Object> model,
                        jakarta.servlet.http.HttpServletRequest request,
                        jakarta.servlet.http.HttpServletResponse response) {
                    response.setStatus(200);
                }
            };
        }
    }

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private FollowService followService;

    @Autowired
    private UserService userService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecentSearchService recentSearchService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM Product").executeUpdate();
            entityManager.createQuery("DELETE FROM Exercise").executeUpdate();
            entityManager.createQuery("DELETE FROM Hashtag").executeUpdate();
            entityManager.createQuery("DELETE FROM RecentSearch").executeUpdate();
            entityManager.createQuery("DELETE FROM Follow").executeUpdate();
            entityManager.createQuery("DELETE FROM User").executeUpdate();
            return null;
        });

        transactionTemplate.execute(status -> {
            Hashtag tag1 = new Hashtag();
            tag1.setName("testTag1");
            entityManager.persist(tag1);

            Hashtag tag2 = new Hashtag();
            tag2.setName("testTag2");
            entityManager.persist(tag2);

            for (int i = 1; i <= 20; i++) {
                User user = new User();
                user.setUsername("queryuser" + i + "@test.com");
                user.setEmail("queryuser" + i + "@test.com");
                user.setPasswordHash("$2a$10$dummyhash");
                user.setEmailVerified(true);
                entityManager.persist(user);

                Product product = new Product();
                product.setTitle("Query Test Product " + i);
                product.setDescription("Desc " + i);
                product.setImageUrl("test" + i + ".jpg");
                product.setTotalQuantity(10);
                product.setAvailableQuantity(10);
                product.setReservationStatus(Product.ReservationStatus.AVAILABLE);
                product.setUser(user);

                Set<Hashtag> tags = new HashSet<>();
                tags.add(tag1);
                if (i % 2 == 0) tags.add(tag2);
                product.setHashtags(tags);

                entityManager.persist(product);
            }

            for (int i = 1; i <= 4; i++) {
                Exercise exercise = new Exercise();
                exercise.setName("Query Test Exercise " + i);
                Set<Hashtag> tags = new HashSet<>();
                tags.add(tag1);
                if (i % 2 == 0) tags.add(tag2);
                exercise.setHashtags(tags);
                entityManager.persist(exercise);
            }
            return null;
        });
    }

    @Test
    @DisplayName("getAllProductsWithDetails - fetch join으로 user/hashtag 즉시 로딩 확인")
    void getAllProductsWithDetails_shouldFetchJoinUserAndHashtags() {
        // OSIV=false 상태에서 트랜잭션 밖 접근 → fetch join 없으면 LazyInitializationException
        List<Product> products = productService.getAllProductsWithDetails();

        assertThat(products).hasSize(20);

        // fetch join 적용 검증: 트랜잭션 종료 후에도 lazy proxy 접근 가능
        for (Product product : products) {
            assertThat(product.getUser()).isNotNull();
            assertThat(product.getUser().getUsername()).isNotBlank();
            assertThat(product.getHashtags()).isNotNull();
        }
    }

    @Test
    @DisplayName("상품 20개 조회 시 전체 데이터 정합성 확인")
    void getAllProductsWithDetails_dataIntegrity() {
        List<Product> products = productService.getAllProductsWithDetails();

        assertThat(products).hasSize(20);

        // 짝수 번째 상품은 해시태그 2개, 홀수는 1개
        long twoTagCount = products.stream()
                .filter(p -> p.getHashtags().size() == 2)
                .count();
        assertThat(twoTagCount).isEqualTo(10);
    }

    @Test
    @DisplayName("getAllProductsWithDetails - 상품 수가 늘어도 조회 SQL은 1회로 유지")
    void getAllProductsWithDetails_shouldKeepQueryCountConstant() {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<Product> products = productService.getAllProductsWithDetails();
        for (Product product : products) {
            product.getUser().getUsername();
            product.getHashtags().size();
        }

        assertThat(products).hasSize(20);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/products - 응답 변환까지 포함해 쿼리 수를 3회 이하로 유지")
    void getProductsApi_shouldKeepQueryCountAtOrBelowDocumentedAfterCount() throws Exception {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("GET /api/products - 인증 사용자의 팔로우 여부를 bulk lookup으로 응답")
    void getProductsApi_withAuthentication_shouldBatchFollowLookup() throws Exception {
        List<Long> userIds = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u.id FROM User u ORDER BY u.id", Long.class)
                        .setMaxResults(5)
                        .getResultList());
        Long followerId = userIds.get(0);
        List<Long> followedIds = List.of(userIds.get(1), userIds.get(3));

        transactionTemplate.execute(status -> {
            User follower = entityManager.find(User.class, followerId);
            for (Long followedId : followedIds) {
                Follow follow = new Follow();
                follow.setFollower(follower);
                follow.setFollowed(entityManager.find(User.class, followedId));
                entityManager.persist(follow);
            }
            return null;
        });

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        String response = mockMvc.perform(get("/api/products")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "queryuser1@test.com",
                                "n/a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> products = objectMapper.readValue(
                response,
                new TypeReference<>() {
                });
        List<Boolean> followedFlags = products.stream()
                .map(product -> product.containsKey("followedByCurrentUser")
                        ? product.get("followedByCurrentUser")
                        : product.get("isFollowedByCurrentUser"))
                .map(Boolean.class::cast)
                .toList();

        assertThat(followedFlags).contains(true, false);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("getFollowedUserIds - 후보 사용자 팔로우 여부를 단일 쿼리로 일괄 조회")
    void getFollowedUserIds_shouldBatchLookupCandidatesInSingleQuery() {
        List<Long> userIds = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u.id FROM User u ORDER BY u.id", Long.class)
                        .setMaxResults(5)
                        .getResultList());
        Long followerId = userIds.get(0);
        List<Long> followedIds = List.of(userIds.get(1), userIds.get(3));

        transactionTemplate.execute(status -> {
            User follower = entityManager.find(User.class, followerId);
            for (Long followedId : followedIds) {
                Follow follow = new Follow();
                follow.setFollower(follower);
                follow.setFollowed(entityManager.find(User.class, followedId));
                entityManager.persist(follow);
            }
            return null;
        });

        User follower = transactionTemplate.execute(status ->
                entityManager.find(User.class, followerId));
        List<User> candidates = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u FROM User u WHERE u.id IN :ids ORDER BY u.id", User.class)
                        .setParameter("ids", userIds.subList(1, userIds.size()))
                        .getResultList());
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        Set<Long> actualFollowedIds = followService.getFollowedUserIds(follower, candidates);

        assertThat(actualFollowedIds).containsExactlyInAnyOrderElementsOf(followedIds);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ranking data path - 상위 사용자, 최근 상품, 팔로우 여부 조회를 bounded query로 유지")
    void rankingDataPath_shouldKeepQueryCountBounded() {
        List<Long> userIds = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u.id FROM User u ORDER BY u.id", Long.class)
                        .setMaxResults(6)
                        .getResultList());
        Long currentUserId = userIds.get(0);

        transactionTemplate.execute(status -> {
            User currentUser = entityManager.find(User.class, currentUserId);
            for (Long followedId : List.of(userIds.get(1), userIds.get(2), userIds.get(3))) {
                Follow follow = new Follow();
                follow.setFollower(currentUser);
                follow.setFollowed(entityManager.find(User.class, followedId));
                entityManager.persist(follow);
            }
            User extraFollower = entityManager.find(User.class, userIds.get(4));
            Follow rankBoost = new Follow();
            rankBoost.setFollower(extraFollower);
            rankBoost.setFollowed(entityManager.find(User.class, userIds.get(1)));
            entityManager.persist(rankBoost);
            return null;
        });

        User currentUser = transactionTemplate.execute(status ->
                entityManager.find(User.class, currentUserId));
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<User> topUsers = userService.getTopUsersByFollowerCount(10);
        List<Product> recentProducts = productService.getRecentProductsByUsers(topUsers, 5);
        Set<Long> followedIds = followService.getFollowedIds(currentUser, topUsers);
        Map<Long, List<Product>> productsByUserId = recentProducts.stream()
                .collect(java.util.stream.Collectors.groupingBy(product -> product.getUser().getId()));

        assertThat(topUsers).hasSize(10);
        assertThat(recentProducts).isNotEmpty();
        assertThat(productsByUserId).isNotEmpty();
        assertThat(followedIds).contains(userIds.get(1), userIds.get(2), userIds.get(3));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("GET /ranking - ranking model 구성과 query-count guard를 같은 HTTP 경로에서 검산")
    void rankingPage_shouldRenderModelAndKeepQueryCountBounded() throws Exception {
        List<Long> userIds = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u.id FROM User u ORDER BY u.id", Long.class)
                        .setMaxResults(6)
                        .getResultList());
        Long currentUserId = userIds.get(0);

        transactionTemplate.execute(status -> {
            User currentUser = entityManager.find(User.class, currentUserId);
            for (Long followedId : List.of(userIds.get(1), userIds.get(2), userIds.get(3))) {
                Follow follow = new Follow();
                follow.setFollower(currentUser);
                follow.setFollowed(entityManager.find(User.class, followedId));
                entityManager.persist(follow);
            }
            User extraFollower = entityManager.find(User.class, userIds.get(4));
            Follow rankBoost = new Follow();
            rankBoost.setFollower(extraFollower);
            rankBoost.setFollowed(entityManager.find(User.class, userIds.get(1)));
            entityManager.persist(rankBoost);
            return null;
        });

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        MvcResult result = mockMvc.perform(get("/ranking")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "queryuser1@test.com",
                                "n/a")))
                .andExpect(status().isOk())
                .andExpect(view().name("ranking"))
                .andExpect(model().attributeExists("topUsers", "currentUser"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<User> topUsers = (List<User>) result.getModelAndView()
                .getModel()
                .get("topUsers");

        assertThat(topUsers).hasSize(10);
        assertThat(topUsers)
                .anySatisfy(user -> assertThat(user.isFollowedByCurrentUser()).isTrue());
        assertThat(topUsers)
                .allSatisfy(user -> assertThat(user.getRecentProducts()).isNotNull());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("상품 목록 조회 경로의 EXPLAIN plan을 MySQL Testcontainers에서 확인")
    void productListQuery_shouldExposeExplainPlanForEvidence() {
        List<Map<String, Object>> explainRows = jdbcTemplate.queryForList("""
                        EXPLAIN
                        SELECT DISTINCT p.id
                        FROM products p
                        LEFT JOIN `user` u ON p.user_id = u.id
                        LEFT JOIN product_hashtags ph ON p.id = ph.product_id
                        LEFT JOIN hashtags h ON ph.hashtag_id = h.id
                        """);
        List<String> accessedAliases = explainRows.stream()
                .map(row -> String.valueOf(row.get("table")))
                .toList();

        assertThat(accessedAliases).contains("p", "u", "ph", "h");
    }

    @Test
    @DisplayName("GET /api/products/{id} - 단건 조회도 user/hashtag를 응답 변환 전에 로딩")
    void getProductApi_shouldFetchUserAndHashtagsForResponseConversion() throws Exception {
        Long productId = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT p.id FROM Product p ORDER BY p.id", Long.class)
                        .setMaxResults(1)
                        .getSingleResult());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.user.username").isNotEmpty())
                .andExpect(jsonPath("$.hashtags.length()").value(1));
    }

    @Test
    @DisplayName("searchProducts - 검색 결과 DTO 접근에 필요한 user/hashtag를 상수 쿼리로 조회")
    void searchProducts_shouldFetchUserAndHashtagsWithoutNPlusOne() {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<Product> products = productService.searchProducts("Query Test Product");
        for (Product product : products) {
            product.getUser().getUsername();
            product.getHashtags().size();
        }

        assertThat(products).hasSize(20);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchProductsByHashtags - 해시태그 검색 결과도 DTO 접근 시 N+1을 만들지 않음")
    void searchProductsByHashtags_shouldFetchUserAndHashtagsWithoutNPlusOne() {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<Product> products = productService.searchProductsByHashtags(Set.of("testTag2"));
        for (Product product : products) {
            product.getUser().getUsername();
            product.getHashtags().size();
        }

        assertThat(products).hasSize(10);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAllExercises - 운동 추천/검색 DTO 변환에 필요한 hashtag를 SQL 1회로 조회")
    void getAllExercises_shouldFetchHashtagsWithoutNPlusOne() {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<Exercise> exercises = exerciseService.getAllExercises();
        for (Exercise exercise : exercises) {
            exercise.getHashtags().size();
        }

        assertThat(exercises).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recent search 저장은 같은 사용자/키워드 동시 요청에서도 한 줄로 유지")
    void recentSearchUpsert_shouldKeepSingleRowForSameUserAndKeywordUnderConcurrency() throws Exception {
        User user = transactionTemplate.execute(status ->
                entityManager
                        .createQuery("SELECT u FROM User u ORDER BY u.id", User.class)
                        .setMaxResults(1)
                        .getSingleResult());
        int requestCount = 12;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(executorService.submit(() -> {
                startGate.await();
                recentSearchService.addOrUpdateRecentSearch(user, "outdoor");
                return null;
            }));
        }

        startGate.countDown();
        for (Future<Void> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        Long rowCount = transactionTemplate.execute(status ->
                entityManager
                        .createQuery(
                                "SELECT COUNT(r) FROM RecentSearch r WHERE r.user.id = :userId AND r.keyword = :keyword",
                                Long.class)
                        .setParameter("userId", user.getId())
                        .setParameter("keyword", "outdoor")
                        .getSingleResult());

        assertThat(rowCount).isEqualTo(1L);
    }
}
