package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.OrderDataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Evaluates and upgrades a user's membership level based on annual consumption.
 */
@Service
public class MemberLevelService {

    private static final Logger log = LoggerFactory.getLogger(MemberLevelService.class);

    private static final BigDecimal PLATINUM_THRESHOLD = new BigDecimal("20000");
    private static final BigDecimal GOLD_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal SILVER_THRESHOLD = new BigDecimal("1000");

    private final LoyaltyAccountRepository accountRepository;

    private final OrderDataFetcher orderDataFetcher;

    public MemberLevelService(LoyaltyAccountRepository accountRepository,
                              OrderDataFetcher orderDataFetcher) {
        this.accountRepository = accountRepository;
        this.orderDataFetcher = orderDataFetcher;
    }

    /**
     * Evaluate the user's annual consumption and upgrade their membership
     * level if thresholds are met.
     *
     * <p>Thresholds:
     * <ul>
     *   <li>PLATINUM: annual consumption >= 20,000</li>
     *   <li>GOLD:    annual consumption >= 5,000</li>
     *   <li>SILVER:  annual consumption >= 1,000</li>
     *   <li>NORMAL:  otherwise</li>
     * </ul>
     *
     * @param userId the user ID
     * @return the new (or unchanged) membership level
     */
    @Transactional
    public MemberLevel evaluateAndUpgrade(Long userId) {
        BigDecimal annual = orderDataFetcher.getAnnualConsumption(userId);

        MemberLevel newLevel;
        if (annual.compareTo(PLATINUM_THRESHOLD) >= 0) {
            newLevel = MemberLevel.PLATINUM;
        } else if (annual.compareTo(GOLD_THRESHOLD) >= 0) {
            newLevel = MemberLevel.GOLD;
        } else if (annual.compareTo(SILVER_THRESHOLD) >= 0) {
            newLevel = MemberLevel.SILVER;
        } else {
            newLevel = MemberLevel.NORMAL;
        }

        LoyaltyAccount account = getOrCreateAccount(userId);
        MemberLevel oldLevel = account.getMemberLevel();
        account.setAnnualConsumption(annual);
        account.setMemberLevel(newLevel);
        accountRepository.save(account);

        if (oldLevel != newLevel) {
            log.info("User {} membership upgraded: {} -> {}", userId, oldLevel, newLevel);
        }

        return newLevel;
    }

    private LoyaltyAccount getOrCreateAccount(Long userId) {
        Optional<LoyaltyAccount> existing = accountRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setTotalPoints(0);
        account.setAvailablePoints(0);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        account.setMemberLevel(MemberLevel.NORMAL);
        account.setAnnualConsumption(BigDecimal.ZERO);
        return accountRepository.save(account);
    }
}
