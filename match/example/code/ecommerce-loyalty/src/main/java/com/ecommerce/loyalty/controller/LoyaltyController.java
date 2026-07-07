package com.ecommerce.loyalty.controller;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.loyalty.dto.MemberLevelResponse;
import com.ecommerce.loyalty.dto.PointsEstimateRequest;
import com.ecommerce.loyalty.dto.PointsEstimateResponse;
import com.ecommerce.loyalty.dto.PointsHistoryResponse;
import com.ecommerce.loyalty.dto.PointsResponse;
import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import com.ecommerce.loyalty.service.MemberLevelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User-facing loyalty endpoints under /api/v1/loyalty.
 * Requires USER role (or is implicitly user-scoped via JWT).
 */
@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyController {

    private final LoyaltyPointService loyaltyPointService;
    private final MemberLevelService memberLevelService;
    private final PointsTransactionRepository transactionRepository;

    public LoyaltyController(LoyaltyPointService loyaltyPointService,
                             MemberLevelService memberLevelService,
                             PointsTransactionRepository transactionRepository) {
        this.loyaltyPointService = loyaltyPointService;
        this.memberLevelService = memberLevelService;
        this.transactionRepository = transactionRepository;
    }

    // ---- GET /points ----

    @GetMapping("/points")
    public ResponseEntity<PointsResponse> getPoints() {
        Long userId = getCurrentUserId();
        LoyaltyAccount account = loyaltyPointService.getAccountByUserId(userId);
        PointsResponse resp = new PointsResponse();
        resp.setUserId(userId);
        resp.setTotalPoints(account.getTotalPoints());
        resp.setAvailablePoints(account.getAvailablePoints());
        resp.setFrozenPoints(account.getFrozenPoints());
        resp.setMemberLevel(account.getMemberLevel().name());
        resp.setMemberLevelName(formatLevelName(account.getMemberLevel()));
        return ResponseEntity.ok(resp);
    }

    // ---- POST /points/estimate-redeem ----

    @PostMapping("/points/estimate-redeem")
    public ResponseEntity<PointsEstimateResponse> estimateRedeem(@RequestBody PointsEstimateRequest request) {
        Long userId = getCurrentUserId();
        LoyaltyAccount account = loyaltyPointService.getAccountByUserId(userId);

        int maxRedeemable = loyaltyPointService.estimateRedeemPoints(request.getOrderAmount(), userId);
        int actual = Math.min(request.getRedeemPoints(), maxRedeemable);
        BigDecimal redeemAmount = BigDecimal.valueOf(actual)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.DOWN);

        PointsEstimateResponse resp = new PointsEstimateResponse();
        resp.setMaxRedeemablePoints(maxRedeemable);
        resp.setActualRedeemPoints(actual);
        resp.setRedeemAmount(redeemAmount);
        resp.setRemainingPoints(account.getAvailablePoints() - actual);
        return ResponseEntity.ok(resp);
    }

    // ---- GET /points/history ----

    @GetMapping("/points/history")
    public ResponseEntity<PageResponse<PointsHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PointsTransaction> txPage = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<PointsHistoryResponse> items = txPage.getContent().stream()
                .map(tx -> {
                    PointsHistoryResponse item = new PointsHistoryResponse();
                    item.setId(tx.getId());
                    item.setType(tx.getType().name());
                    item.setAmount(tx.getAmount());
                    item.setBalance(tx.getBalance());
                    item.setDescription(tx.getDescription());
                    item.setCreatedAt(tx.getCreatedAt());
                    return item;
                })
                .collect(Collectors.toList());

        PageResponse<PointsHistoryResponse> resp = PageResponse.of(
                txPage.getNumber(), txPage.getSize(), txPage.getTotalElements(), items);
        return ResponseEntity.ok(resp);
    }

    // ---- GET /member-level ----

    @GetMapping("/member-level")
    public ResponseEntity<MemberLevelResponse> getMemberLevel() {
        Long userId = getCurrentUserId();
        // Re-evaluate before returning to keep level current
        MemberLevel level = memberLevelService.evaluateAndUpgrade(userId);
        LoyaltyAccount account = loyaltyPointService.getAccountByUserId(userId);

        MemberLevelResponse resp = new MemberLevelResponse();
        resp.setLevel(level.name());
        resp.setLevelName(formatLevelName(level));
        resp.setMultiplier(level.getMultiplier());
        resp.setAnnualConsumption(account.getAnnualConsumption());
        resp.setNextLevelCondition(getNextLevelCondition(level));
        return ResponseEntity.ok(resp);
    }

    // ---- helpers ----

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw com.ecommerce.common.exception.AuthorizationException.unauthorized("Not authenticated");
        }
        return Long.parseLong(authentication.getName());
    }

    private String formatLevelName(MemberLevel level) {
        switch (level) {
            case NORMAL:   return "Normal Member";
            case SILVER:   return "Silver Member";
            case GOLD:     return "Gold Member";
            case PLATINUM: return "Platinum Member";
            default:       return level.name();
        }
    }

    private String getNextLevelCondition(MemberLevel level) {
        switch (level) {
            case NORMAL:   return "Annual consumption >= 1,000 to reach Silver";
            case SILVER:   return "Annual consumption >= 5,000 to reach Gold";
            case GOLD:     return "Annual consumption >= 20,000 to reach Platinum";
            case PLATINUM: return "Already at highest level";
            default:       return "";
        }
    }
}
