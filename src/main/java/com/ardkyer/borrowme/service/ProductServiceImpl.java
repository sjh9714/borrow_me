package com.ardkyer.borrowme.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.ardkyer.borrowme.entity.*;
import com.ardkyer.borrowme.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CommentRepository commentRepository;
    private final HashtagRepository hashtagRepository;
    private final AmazonS3 amazonS3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository,
                            CommentRepository commentRepository,
                            HashtagRepository hashtagRepository,
                            AmazonS3 amazonS3Client) {
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.hashtagRepository = hashtagRepository;
        this.amazonS3Client = amazonS3Client;
    }

    @Override
    @Transactional
    public Product uploadProduct(Product product, MultipartFile file, Set<String> hashtagNames) throws IOException {
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucketName, fileName, file.getInputStream(), metadata);

        product.setImageUrl(fileName);

        Set<Hashtag> hashtags = convertNamesToHashtags(hashtagNames);
        product.setHashtags(hashtags);

        return productRepository.save(product);
    }

    private Set<Hashtag> convertNamesToHashtags(Set<String> hashtagNames) {
        if (hashtagNames.isEmpty()) return Collections.emptySet();

        List<Hashtag> existing = hashtagRepository.findByNameIn(hashtagNames);
        Map<String, Hashtag> existingMap = existing.stream()
                .collect(Collectors.toMap(Hashtag::getName, h -> h));

        Set<Hashtag> result = new HashSet<>(existing);
        List<Hashtag> newHashtags = hashtagNames.stream()
                .filter(name -> !existingMap.containsKey(name))
                .map(name -> {
                    Hashtag newHashtag = new Hashtag();
                    newHashtag.setName(name);
                    return newHashtag;
                })
                .collect(Collectors.toList());

        if (!newHashtags.isEmpty()) {
            result.addAll(hashtagRepository.saveAll(newHashtags));
        }
        return result;
    }

    @Override
    public S3Object getProductFile(String fileName) {
        return amazonS3Client.getObject(bucketName, fileName);
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        return productRepository.findByIdWithUserAndHashtags(id);
    }

    @Override
    public List<Product> getProductsByUser(User user) {
        return productRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    @Transactional
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Optional<Product> productOptional = productRepository.findById(id);
        if (productOptional.isPresent()) {
            Product product = productOptional.get();
            try {
                amazonS3Client.deleteObject(bucketName, product.getImageUrl());
            } catch (Exception e) {
                log.error("Failed to delete image from S3: " + e.getMessage());
            }
            productRepository.deleteById(id);
        } else {
            throw new RuntimeException("Product not found with id: " + id);
        }
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> getAllProductsWithDetails() {
        return productRepository.findAllWithUserAndHashtags();
    }

    @Override
    public List<Product> getAllProductsWithComments() {
        return productRepository.findAllWithComments();
    }

    @Override
    public List<Product> getAllProductsWithSortedComments() {
        List<Product> products = productRepository.findAll();
        products.forEach(product -> {
            Page<Comment> commentsPage = commentRepository.findByProductOrderByCreatedAtDesc(product, PageRequest.of(0, 5));
            product.setComments(new ArrayList<>(commentsPage.getContent()));
        });
        return products;
    }

    @Override
    public void saveHashtagsFromDescription(String description) {
        if (description != null && !description.trim().isEmpty()) {
            Set<String> hashtags = Arrays.stream(description.split(" "))
                    .filter(tag -> tag.startsWith("#"))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            convertNamesToHashtags(hashtags);
        }
    }

    @Override
    public List<Product> getReservedProductsByUser(User user) {
        return productRepository.findByReservationsUser(user);
    }

    @Override
    public boolean isAvailableForReservation(Long productId, int quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            return product.getAvailableQuantity() >= quantity &&
                    product.getReservationStatus() != Product.ReservationStatus.OUT_OF_STOCK;
        }
        return false;
    }

    @Override
    public Product updateAvailableQuantity(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setAvailableQuantity(product.getAvailableQuantity() - quantity);
        return productRepository.save(product);
    }

    @Override
    public List<Product> searchProducts(String query) {
        return productRepository.searchByKeywordWithDetails(query);
    }

    @Override
    public List<Product> searchProductsByHashtags(Set<String> hashtags) {
        return productRepository.findByHashtagsNameInWithDetails(hashtags);
    }

    @Override
    public List<Product> getRandomRecentProducts(int count) {
        List<Product> recentProducts = productRepository.findRecentProducts(PageRequest.of(0, count));
        Collections.shuffle(recentProducts);
        return recentProducts.stream().limit(count).collect(Collectors.toList());
    }

    @Override
    public List<Product> getRecentProductsByUser(User user, int limit) {
        return productRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, limit));
    }

    @Override
    public List<Product> getRecentProductsByUsers(List<User> users, int limit) {
        if (users.isEmpty()) return Collections.emptyList();
        return productRepository.findByUserInOrderByCreatedAtDesc(users, PageRequest.of(0, users.size() * limit));
    }

    @Override
    @Transactional
    public Product updateProductWithImage(Product product, MultipartFile file) throws IOException {
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            try {
                amazonS3Client.deleteObject(bucketName, product.getImageUrl());
            } catch (Exception e) {
                log.error("Failed to delete old image from S3: " + e.getMessage());
            }
        }

        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        try {
            amazonS3Client.putObject(bucketName, fileName, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new IOException("Failed to upload image to S3: " + e.getMessage());
        }

        product.setImageUrl(fileName);
        return productRepository.save(product);
    }

    private String getFileExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}
