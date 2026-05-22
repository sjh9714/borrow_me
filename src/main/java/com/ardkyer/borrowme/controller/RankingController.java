package com.ardkyer.borrowme.controller;

import com.ardkyer.borrowme.entity.User;
import com.ardkyer.borrowme.entity.Product;
import com.ardkyer.borrowme.service.UserService;
import com.ardkyer.borrowme.service.ProductService;
import com.ardkyer.borrowme.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class RankingController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private FollowService followService;

    @GetMapping("/ranking")
    public String showRanking(Model model, Authentication authentication) {
        List<User> topUsers = userService.getTopUsersByFollowerCount(10);

        User currentUser = null;
        if (authentication != null) {
            currentUser = userService.findByUsername(authentication.getName());
        }

        List<Product> allRecentProducts = productService.getRecentProductsByUsers(topUsers, 5);
        Map<Long, List<Product>> productsByUserId = allRecentProducts.stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getId()));

        Set<Long> followedIds = Collections.emptySet();
        if (currentUser != null) {
            followedIds = followService.getFollowedIds(currentUser, topUsers);
        }

        for (User user : topUsers) {
            if (user.getAvatarUrl() == null) {
                user.setAvatarUrl("/default-avatar.png");
            }
            user.setRecentProducts(productsByUserId.getOrDefault(user.getId(), Collections.emptyList()));
            user.setFollowedByCurrentUser(followedIds.contains(user.getId()));
        }

        model.addAttribute("topUsers", topUsers);
        model.addAttribute("currentUser", currentUser);
        return "ranking";
    }
}
