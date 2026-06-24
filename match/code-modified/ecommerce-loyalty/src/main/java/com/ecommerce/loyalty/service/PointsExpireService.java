package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles the periodic expiration of loyalty points.
 *
 * <p>Points expire after 12 calendar months. This service scans for
 * points that have passed their expiration date and deducts them
 * from the user's available balance.
 */
@Service
public class PointsExpireService {

    private static final Logger log = LoggerFactory.getLogger(PointsExpireService.class);

    private final LoyaltyAccountRepository accountRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    public PointsExpireService(LoyaltyAccountRepository accountRepository,
                               PointsTransactionRepository pointsTransactionRepository) {
        this.accountRepository = accountRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
    }

    /**
     * Runs the points expiration task.
     * Scans for points transactions past their expiration date and
     * deducts expired points from the user's available balance.
     */
    @Transactional
    public void expire() {
        log.info("PointsExpireService.expire() starting");

        LocalDateTime now = LocalDateTime.now();
        List<PointsTransaction> expiredTransactions = pointsTransactionRepository
                .findByTypeAndExpiresAtBefore(PointsTransactionType.EARN, now);

        if (expiredTransactions.isEmpty()) {
            log.info("No expired points found");
            return;
        }

        int totalExpired = 0;
        int totalTransactions = 0;

        for (PointsTransaction tx : expiredTransactions) {
            if (tx.getType() != PointsTransactionType.EARN) {
                continue;
            }

            var accountOpt = accountRepository.findByUserId(tx.getUserId());
            if (accountOpt.isEmpty()) {
                continue;
            }

            LoyaltyAccount account = accountOpt.get();
            int expireAmount = tx.getAmount();

            // Deduct from available points
            account.setAvailablePoints(account.getAvailablePoints() - expireAmount);
            account.setExpiredPoints(account.getExpiredPoints() + expireAmount);
            accountRepository.save(account);

            // Create expiration record
            PointsTransaction expireRecord = new PointsTransaction();
            expireRecord.setUserId(tx.getUserId());
            expireRecord.setAmount(-expireAmount);
            expireRecord.setType(PointsTransactionType.EXPIRE);
            expireRecord.setBizType("EXPIRE");
            expireRecord.setBizId(tx.getBizId());
            expireRecord.setDescription("Points expired");
            expireRecord.setExpiresAt(now);
            expireRecord.setBalance(account.getAvailablePoints());
            pointsTransactionRepository.save(expireRecord);

            totalExpired += expireAmount;
            totalTransactions++;

            log.debug("Expired {} points for userId={}, bizId={}",
                    expireAmount, tx.getUserId(), tx.getBizId());
        }

        log.info("Points expiration complete: {} transactions, {} total points expired",
                totalTransactions, totalExpired);
    }
}
