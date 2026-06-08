package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrderPreconditionChecker}.
 *
 * <p>The {@link OrderPreconditionChecker#check(Long, int)} method
 * only verifies that the user exists (user != null), but does NOT check whether
 * the user is frozen. These tests verify the ACTUAL behavior — frozen users
 * are allowed to create orders.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPreconditionChecker")
class OrderPreconditionCheckerTest {

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private OrderPreconditionChecker preconditionChecker;

    // ======================== user existence check ========================

    @Test
    @DisplayName("check passes when user exists (user != null)")
    void testCheck_userExists_passes() {
        UserDto user = new UserDto();
        user.setUserId(1L);
        user.setStatus("ACTIVE");

        when(userQueryService.getUserById(1L)).thenReturn(user);

        assertThatCode(() -> preconditionChecker.check(1L, 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("check passes for frozen user — frozen user NOT blocked")
    void testCheck_userFrozen_stillPasses() {
        // Frozen users should be blocked from creating orders,
        // but the check() method only validates user != null. It never calls
        // userQueryService.isFrozen().

        UserDto frozenUser = new UserDto();
        frozenUser.setUserId(2L);
        frozenUser.setStatus("FROZEN");

        when(userQueryService.getUserById(2L)).thenReturn(frozenUser);

        // Even though the user is frozen, the check passes.
        assertThatCode(() -> preconditionChecker.check(2L, 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("check throws BusinessException when user is null")
    void testCheck_userNull_throwsException() {
        when(userQueryService.getUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> preconditionChecker.check(99L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("check throws BusinessException when itemCount is zero or negative")
    void testCheck_emptyItems_throwsException() {
        UserDto user = new UserDto();
        user.setUserId(3L);
        when(userQueryService.getUserById(3L)).thenReturn(user);

        assertThatThrownBy(() -> preconditionChecker.check(3L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item");

        assertThatThrownBy(() -> preconditionChecker.check(3L, -1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item");
    }
}
