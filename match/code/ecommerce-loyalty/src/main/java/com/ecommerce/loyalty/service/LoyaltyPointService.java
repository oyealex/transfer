package com.ecommerce.loyalty.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.query.LoyaltyCommandService;
import com.ecommerce.loyalty.query.LoyaltyQueryService;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Core service for points operations: query, earn, redeem, and estimate.
 *
 * <p>Implements both {@link LoyaltyQueryService} (reads) and
 * {@link LoyaltyCommandService} (writes).
 */
@Service
public class LoyaltyPointService implements LoyaltyQueryService, LoyaltyCommandService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyPointService.class);

    /** 100 points = 1 yuan */
    private static final int POINTS_PER_YUAN = 100;

    /** Maximum redeemable points per order (10,000 points = 100 yuan) */
    private static final int MAX_REDEEM_POINTS = 10_000;

    /** Maximum redeem ratio: points deduction cannot exceed 50% of order amount */
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    private static final int DEFAULT_EXPIRE_MONTHS = 12;

    private final LoyaltyAccountRepository accountRepository;
    private final PointsTransactionRepository transactionRepository;

    public LoyaltyPointService(LoyaltyAccountRepository accountRepository,
                               PointsTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ======================== LoyaltyQueryService ========================

    @Override
    public int getAvailablePoints(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return account.getAvailablePoints();
    }

    @Override
    public int estimateRedeemPoints(BigDecimal orderAmount, Long userId) {
        LoyaltyAccount account = getAccount(userId);
        int available = account.getAvailablePoints();

        // 50% of order amount in points: orderAmount * 100 * 0.5
        int ratioCapped = orderAmount.multiply(new BigDecimal(POINTS_PER_YUAN))
                .multiply(MAX_REDEEM_RATIO)
                .setScale(0, RoundingMode.DOWN)
                .intValue();

        // min(available, MAX_REDEEM_POINTS, ratioCapped)
        return Math.min(Math.min(available, MAX_REDEEM_POINTS), ratioCapped);
    }

    @Override
    public MemberLevel getMemberLevel(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return account.getMemberLevel();
    }

    @Override
    public double getMemberMultiplier(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return account.getMemberLevel().getMultiplier();
    }

    // ======================== LoyaltyCommandService ========================

    @Override
    @Transactional
    public int earnPaymentPoints(Long userId, BigDecimal orderAmount, double activityMultiplier) {
        int points = calcOrderPoints(orderAmount, userId, activityMultiplier);
        if (points <= 0) {
            return 0;
        }
        earnPoints(userId, points, "ORDER_PAYMENT", null, "Order payment reward");
        return points;
    }

    @Override
    @Transactional
    public int redeemPoints(Long userId, int points, BigDecimal orderAmount) {
        LoyaltyAccount account = getAccount(userId);

        // Apply 10,000 cap and 50% cap
        int maxRedeemable = estimateRedeemPoints(orderAmount, userId);
        int actual = Math.min(points, maxRedeemable);

        if (actual <= 0) {
            return 0;
        }

        account.setAvailablePoints(account.getAvailablePoints() - actual);
        account.setRedeemedPoints(account.getRedeemedPoints() + actual);
        account.setTotalPoints(account.getTotalPoints() - actual);
        accountRepository.save(account);

        PointsTransaction tx = new PointsTransaction();
        tx.setUserId(userId);
        tx.setType(PointsTransactionType.REDEEM);
        tx.setAmount(-actual);
        tx.setBalance(account.getAvailablePoints());
        tx.setBizType("ORDER_REDEEM");
        tx.setDescription("Points redeem, deducted " + actual + " points");
        tx.setExpiresAt(null);
        transactionRepository.save(tx);

        log.info("Redeemed {} points for userId={}, balance={}", actual, userId, account.getAvailablePoints());
        return actual;
    }

    @Override
    public void expirePoints() {
        // Delegated to PointsExpireService
    }

    // ======================== Domain methods ========================

    /**
     * Calculate order points.
     *
     * <p>Missing the activityMultiplier factor.
     * The design spec says: orderPoints = amount * levelMultiplier * activityMultiplier.
     * This implementation only multiplies by the level multiplier, ignoring the
     * activity parameter entirely. Promotional double-points events will have no effect.
     *
     * @param amount             the order payable amount
     * @param userId             the user ID
     * @param activityMultiplier promotional activity coefficient (default 1.0) — IGNORED
     * @return calculated points
     */
    public int calcOrderPoints(BigDecimal amount, Long userId, double activityMultiplier) {
        LoyaltyAccount account = getAccount(userId);
        BigDecimal levelMultiplier = BigDecimal.valueOf(account.getMemberLevel().getMultiplier());
        double configuredActivityMultiplier =
                RuntimeConfigRegistry.getDouble("loyalty.activity-multiplier", 1.0d);
        BigDecimal points = amount.multiply(BigDecimal.valueOf(POINTS_PER_YUAN))
                .multiply(levelMultiplier)
                .multiply(BigDecimal.valueOf(activityMultiplier))
                .multiply(BigDecimal.valueOf(configuredActivityMultiplier));
        return points.setScale(0, RoundingMode.DOWN).intValue();
    }

    /**
     * Award points to a user and record the transaction.
     *
     * @param userId      the user ID
     * @param points      number of points to add
     * @param bizType     business type (e.g. "ORDER", "REVIEW")
     * @param bizId       business entity ID
     * @param description human-readable description
     */
    @Transactional
    public void earnPoints(Long userId, int points, String bizType, String bizId, String description) {
        // Fault injection check
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("loyalty-award-points-failure")) {
            throw new RuntimeException("Fault injected: loyalty-award-points-failure");
        }

        LoyaltyAccount account = getAccount(userId);

        account.setTotalPoints(account.getTotalPoints() + points);
        account.setAvailablePoints(account.getAvailablePoints() + points);
        accountRepository.save(account);

        PointsTransaction tx = new PointsTransaction();
        tx.setUserId(userId);
        tx.setType(PointsTransactionType.EARN);
        tx.setAmount(points);
        tx.setBalance(account.getAvailablePoints());
        tx.setBizType(bizType);
        tx.setBizId(bizId);
        tx.setDescription(description);
        tx.setExpiresAt(SystemClockService.now().plusMonths(DEFAULT_EXPIRE_MONTHS));
        transactionRepository.save(tx);

        log.info("Earned {} points for userId={}, balance={}", points, userId, account.getAvailablePoints());
    }

    // ======================== Helpers ========================

    /**
     * Get the loyalty account for a user, or create one with defaults.
     */
    private LoyaltyAccount getAccount(Long userId) {
        return accountRepository.findByUserId(userId)
                .map(this::applyConfigOverrides)
                .orElseGet(() -> createDefaultAccount(userId));
    }

    private LoyaltyAccount createDefaultAccount(Long userId) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        int initialPoints = RuntimeConfigRegistry.getInt("loyalty.points", 0);
        account.setTotalPoints(initialPoints);
        account.setAvailablePoints(initialPoints);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        account.setMemberLevel(resolveConfiguredMemberLevel(MemberLevel.NORMAL));
        account.setAnnualConsumption(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    private LoyaltyAccount applyConfigOverrides(LoyaltyAccount account) {
        boolean changed = false;
        MemberLevel configuredLevel = resolveConfiguredMemberLevel(account.getMemberLevel());
        if (configuredLevel != account.getMemberLevel()) {
            account.setMemberLevel(configuredLevel);
            changed = true;
        }
        return changed ? accountRepository.save(account) : account;
    }

    private MemberLevel resolveConfiguredMemberLevel(MemberLevel fallback) {
        String configured = RuntimeConfigRegistry.getString("loyalty.member-level", null);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return MemberLevel.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Exposed so the controller can build a points response.
     */
    public LoyaltyAccount getAccountByUserId(Long userId) {
        return getAccount(userId);
    }
}
