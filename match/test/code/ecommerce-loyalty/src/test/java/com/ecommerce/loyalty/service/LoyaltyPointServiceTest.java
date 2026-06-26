package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoyaltyPointService}.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyPointServiceTest {

    @Mock
    private LoyaltyAccountRepository accountRepository;

    @Mock
    private PointsTransactionRepository transactionRepository;

    private LoyaltyPointService service;

    @BeforeEach
    void setUp() {
        service = new LoyaltyPointService(accountRepository, transactionRepository);
    }

    // ======================== calcOrderPoints ========================

    /**
     * calcOrderPoints applies the activityMultiplier parameter.
     */
    @Test
    void testCalcOrderPoints_withActivityCoefficient() {
        LoyaltyAccount account = createAccount(1L, MemberLevel.SILVER, 0, 0);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        BigDecimal amount = new BigDecimal("100");
        double activityMultiplier = 2.0;

        int result = service.calcOrderPoints(amount, 1L, activityMultiplier);

        // 100 yuan * 100 points/yuan * 1.1 (SILVER multiplier) * 2.0 activity multiplier
        int expected = 22000;

        assertEquals(expected, result,
                "activityMultiplier=2.0 should double the base order points");
    }

    // ======================== earnPoints ========================

    @Test
    void testEarnPoints_createsTransactionAndUpdatesBalance() {
        LoyaltyAccount account = createAccount(1L, MemberLevel.NORMAL, 0, 0);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        service.earnPoints(1L, 500, "TEST_BIZ", "BIZ-001", "Test earn description");

        // Verify account balance was updated
        ArgumentCaptor<LoyaltyAccount> accountCaptor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        LoyaltyAccount savedAccount = accountCaptor.getValue();
        assertEquals(500, savedAccount.getTotalPoints(), "Total points should increase by 500");
        assertEquals(500, savedAccount.getAvailablePoints(), "Available points should increase by 500");

        // Verify a PointsTransaction record was created
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PointsTransaction tx = txCaptor.getValue();
        assertEquals(1L, tx.getUserId(), "Transaction userId should match");
        assertEquals(PointsTransactionType.EARN, tx.getType(), "Transaction type should be EARN");
        assertEquals(500, tx.getAmount(), "Transaction amount should be 500");
        assertEquals(500, tx.getBalance(), "Transaction balance should reflect new balance");
        assertEquals("TEST_BIZ", tx.getBizType(), "Transaction bizType should match");
        assertEquals("BIZ-001", tx.getBizId(), "Transaction bizId should match");
        assertEquals("Test earn description", tx.getDescription(), "Transaction description should match");
        assertNotNull(tx.getExpiresAt(), "EARN transaction should have an expiration date");
    }

    // ======================== redeemPoints ========================

    /**
     * Redeem applies both 10,000-point cap and 50%-of-order cap.
     * Within limits, points are deducted correctly.
     */
    @Test
    void testRedeemPoints_withinLimits_deductsPoints() {
        LoyaltyAccount account = createAccount(1L, MemberLevel.NORMAL, 5000, 5000);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        // orderAmount=100 yuan: 50% cap = 100 * 100 * 0.5 = 5000
        // available=5000, max cap=10000 -> maxRedeemable = min(5000, 10000, 5000) = 5000
        // actual = min(2000, 5000) = 2000
        int redeemed = service.redeemPoints(1L, 2000, BigDecimal.valueOf(100));

        assertEquals(2000, redeemed, "Should redeem exactly 2000 (within all caps)");

        // Verify account state
        ArgumentCaptor<LoyaltyAccount> accountCaptor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        LoyaltyAccount saved = accountCaptor.getValue();
        assertEquals(3000, saved.getAvailablePoints(), "Available points should decrease to 3000");
        assertEquals(2000, saved.getRedeemedPoints(), "Redeemed points should increase to 2000");
        assertEquals(3000, saved.getTotalPoints(), "Total points should decrease to 3000");

        // Verify REDEEM transaction was recorded
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PointsTransaction tx = txCaptor.getValue();
        assertEquals(PointsTransactionType.REDEEM, tx.getType());
        assertEquals(-2000, tx.getAmount());
        assertEquals("ORDER_REDEEM", tx.getBizType());
    }

    /**
     * When requested points exceed the calculated maximum
     * (10,000-point cap or 50% cap), the redemption is clamped.
     */
    @Test
    void testRedeemPoints_exceedsCap_clampedToMax() {
        LoyaltyAccount account = createAccount(1L, MemberLevel.NORMAL, 50000, 50000);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        // orderAmount=100 yuan: 50% cap = 5000
        // available=50000, max=10000 -> maxRedeemable = min(50000, 10000, 5000) = 5000
        // actual = min(50000, 5000) = 5000 (clamped by 50% cap)
        int redeemed = service.redeemPoints(1L, 50000, BigDecimal.valueOf(100));

        assertEquals(5000, redeemed,
                "Should be clamped to 5000 by 50%-of-order cap (not 50000 as requested)");

        ArgumentCaptor<LoyaltyAccount> accountCaptor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        LoyaltyAccount saved = accountCaptor.getValue();
        assertEquals(45000, saved.getAvailablePoints(), "Available points should decrease by 5000");
        assertEquals(5000, saved.getRedeemedPoints(), "Redeemed points should be 5000");
    }

    // ======================== estimateRedeemPoints ========================

    @Test
    void testEstimateRedeem_returnsCorrectEstimate() {
        LoyaltyAccount account = createAccount(1L, MemberLevel.NORMAL, 5000, 5000);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        // orderAmount=100 yuan -> 50% cap = 5000; available=5000; max=10000
        // estimate = min(5000, 10000, 5000) = 5000
        int estimate = service.estimateRedeemPoints(BigDecimal.valueOf(100), 1L);
        assertEquals(5000, estimate, "Estimate should be 5000 (limited by 50% cap and available points)");
    }

    // ======================== getAvailablePoints / getAccountByUserId ========================

    @Test
    void testGetPoints_returnsAccountBalance() {
        LoyaltyAccount account = createAccount(2L, MemberLevel.SILVER, 1200, 1200);
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(account));

        int points = service.getAvailablePoints(2L);
        assertEquals(1200, points, "Available points should match account balance");

        LoyaltyAccount retrieved = service.getAccountByUserId(2L);
        assertEquals(1200, retrieved.getAvailablePoints(), "Account retrieved should have correct balance");
    }

    // ======================== helpers ========================

    private LoyaltyAccount createAccount(Long userId, MemberLevel level, int totalPoints, int availablePoints) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setMemberLevel(level);
        account.setTotalPoints(totalPoints);
        account.setAvailablePoints(availablePoints);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        return account;
    }
}
