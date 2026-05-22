package com.ardkyer.borrowme;

import com.ardkyer.borrowme.entity.Product;
import com.ardkyer.borrowme.entity.Reservation;
import com.ardkyer.borrowme.entity.User;
import com.ardkyer.borrowme.repository.ProductRepository;
import com.ardkyer.borrowme.repository.ReservationRepository;
import com.ardkyer.borrowme.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ReservationConcurrencyTest {

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
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Long productId;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM Reservation").executeUpdate();
            entityManager.createQuery("DELETE FROM Product").executeUpdate();
            entityManager.createQuery("DELETE FROM User").executeUpdate();
            entityManager.clear();
            return null;
        });

        // owner 생성
        User owner = transactionTemplate.execute(status -> {
            User o = new User();
            o.setUsername("owner@test.com");
            o.setEmail("owner@test.com");
            o.setPasswordHash("$2a$10$dummyhash");
            o.setEmailVerified(true);
            entityManager.persist(o);
            return o;
        });

        // product 생성 (재고 50)
        productId = transactionTemplate.execute(status -> {
            Product product = new Product();
            product.setTitle("Concurrency Test Product");
            product.setDescription("Test");
            product.setImageUrl("test.jpg");
            product.setTotalQuantity(50);
            product.setAvailableQuantity(50);
            product.setReservationStatus(Product.ReservationStatus.AVAILABLE);
            product.setUser(entityManager.find(User.class, owner.getId()));
            entityManager.persist(product);
            return product.getId();
        });

        // 100명의 유저 생성
        userIds = Collections.synchronizedList(new ArrayList<>());
        transactionTemplate.execute(status -> {
            for (int i = 1; i <= 100; i++) {
                User user = new User();
                user.setUsername("user" + i + "@test.com");
                user.setEmail("user" + i + "@test.com");
                user.setPasswordHash("$2a$10$dummyhash");
                user.setEmailVerified(true);
                entityManager.persist(user);
                userIds.add(user.getId());
            }
            return null;
        });
    }

    @Test
    @DisplayName("동시 100명 예약 시 재고 50 → 성공 50 + 실패 50")
    void concurrentReservation_shouldRespectStock() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> unexpectedFailures = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    transactionTemplate.execute(status -> {
                        Product product = entityManager.find(Product.class, productId);
                        User user = entityManager.find(User.class, userIds.get(index));
                        reservationService.reserve(product, user, 1);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if ("재고가 부족합니다.".equals(e.getMessage())) {
                        failCount.incrementAndGet();
                    } else {
                        unexpectedFailures.add(e);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unexpectedFailures.add(e);
                } catch (Exception e) {
                    unexpectedFailures.add(e);
                }
            }));
        }

        readyLatch.await(); // 모든 스레드 준비 완료
        startLatch.countDown(); // 동시 시작

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                unexpectedFailures.add(e);
            } catch (Exception e) {
                unexpectedFailures.add(e);
            }
        }
        executor.shutdown();

        // 검증
        Product finalProduct = transactionTemplate.execute(status ->
                productRepository.findById(productId).orElseThrow()
        );

        assertThat(unexpectedFailures)
                .as("동시 예약 실패는 재고 부족 예외만 허용합니다.")
                .isEmpty();
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
        assertThat(reservationRepository.count()).isEqualTo(50);
        assertThat(finalProduct.getAvailableQuantity()).isEqualTo(0);
        assertThat(finalProduct.getReservationStatus()).isEqualTo(Product.ReservationStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고 부족으로 예약 실패 시 예약 row와 재고가 변경되지 않음")
    void failedReservation_shouldNotChangeStockOrCreateReservation() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            Product product = entityManager.find(Product.class, productId);
            User user = entityManager.find(User.class, userIds.get(0));
            reservationService.reserve(product, user, 51);
            return null;
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("재고가 부족합니다.");

        Product finalProduct = transactionTemplate.execute(status ->
                productRepository.findById(productId).orElseThrow()
        );

        assertThat(reservationRepository.count()).isZero();
        assertThat(finalProduct.getAvailableQuantity()).isEqualTo(50);
        assertThat(finalProduct.getReservationStatus()).isEqualTo(Product.ReservationStatus.AVAILABLE);
    }
}
