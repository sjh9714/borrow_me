CREATE TABLE `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255),
    profile_picture VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    avatar_url VARCHAR(255),
    email_verified BIT NOT NULL,
    verification_token VARCHAR(255),
    verification_token_expiry DATETIME(6),
    provider VARCHAR(255),
    provider_id VARCHAR(255),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    roles VARCHAR(255),
    KEY idx_user_roles_user_id (user_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(255) NOT NULL,
    total_quantity INT,
    available_quantity INT,
    reservation_status VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_products_user_id (user_id),
    KEY idx_products_created_at (created_at),
    CONSTRAINT fk_products_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE hashtags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hashtags_name (name)
) ENGINE=InnoDB;

CREATE TABLE product_hashtags (
    product_id BIGINT NOT NULL,
    hashtag_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, hashtag_id),
    KEY idx_product_hashtags_hashtag_id (hashtag_id),
    CONSTRAINT fk_product_hashtags_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_hashtags_hashtag
        FOREIGN KEY (hashtag_id) REFERENCES hashtags (id)
) ENGINE=InnoDB;

CREATE TABLE exercise (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exercise_name (name)
) ENGINE=InnoDB;

CREATE TABLE exercise_hashtag (
    exercise_id BIGINT NOT NULL,
    hashtag_id BIGINT NOT NULL,
    PRIMARY KEY (exercise_id, hashtag_id),
    KEY idx_exercise_hashtag_hashtag_id (hashtag_id),
    CONSTRAINT fk_exercise_hashtag_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    CONSTRAINT fk_exercise_hashtag_hashtag
        FOREIGN KEY (hashtag_id) REFERENCES hashtags (id)
) ENGINE=InnoDB;

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    like_count INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comments_user_id (user_id),
    KEY idx_comments_product_id (product_id),
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_comments_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB;

CREATE TABLE reply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT,
    user_id BIGINT,
    product_id BIGINT,
    content VARCHAR(255) NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_reply_comment_id (comment_id),
    KEY idx_reply_user_id (user_id),
    KEY idx_reply_product_id (product_id),
    CONSTRAINT fk_reply_comment
        FOREIGN KEY (comment_id) REFERENCES comments (id),
    CONSTRAINT fk_reply_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_reply_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB;

CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT,
    user_id BIGINT,
    quantity INT NOT NULL,
    status VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    contact VARCHAR(255),
    rental_start_date DATETIME(6),
    rental_end_date DATETIME(6),
    note TEXT,
    PRIMARY KEY (id),
    KEY idx_reservations_product_id (product_id),
    KEY idx_reservations_user_id (user_id),
    CONSTRAINT fk_reservations_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_likes_user_id (user_id),
    KEY idx_likes_product_id (product_id),
    CONSTRAINT fk_likes_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_likes_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB;

CREATE TABLE comment_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comment_likes_comment_id (comment_id),
    KEY idx_comment_likes_user_id (user_id),
    CONSTRAINT fk_comment_likes_comment
        FOREIGN KEY (comment_id) REFERENCES comments (id),
    CONSTRAINT fk_comment_likes_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE follows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    followed_id BIGINT NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_follows_follower_id (follower_id),
    KEY idx_follows_followed_id (followed_id),
    CONSTRAINT fk_follows_follower
        FOREIGN KEY (follower_id) REFERENCES `user` (id),
    CONSTRAINT fk_follows_followed
        FOREIGN KEY (followed_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    type VARCHAR(255),
    post_title VARCHAR(255),
    commenter_name VARCHAR(255),
    comment_content VARCHAR(255),
    is_read BIT NOT NULL,
    message VARCHAR(255),
    created_at DATETIME(6),
    product_id BIGINT,
    comment_id BIGINT,
    reply_id BIGINT,
    PRIMARY KEY (id),
    KEY idx_notification_user_id (user_id),
    KEY idx_notification_product_id (product_id),
    KEY idx_notification_comment_id (comment_id),
    KEY idx_notification_reply_id (reply_id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_notification_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_notification_comment
        FOREIGN KEY (comment_id) REFERENCES comments (id),
    CONSTRAINT fk_notification_reply
        FOREIGN KEY (reply_id) REFERENCES reply (id)
) ENGINE=InnoDB;

CREATE TABLE recent_search (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    search_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recent_search_user_keyword (user_id, keyword),
    CONSTRAINT fk_recent_search_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB;

CREATE TABLE email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255),
    verification_code VARCHAR(255),
    expiry_date DATETIME(6),
    verified BIT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_email (email)
) ENGINE=InnoDB;
