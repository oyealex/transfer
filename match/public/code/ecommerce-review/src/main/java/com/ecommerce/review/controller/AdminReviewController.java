package com.ecommerce.review.controller;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.review.dto.ReviewApprovalRequest;
import com.ecommerce.review.service.ReviewModerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin review REST controller for moderation endpoints.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private static final Logger log = LoggerFactory.getLogger(AdminReviewController.class);

    private final ReviewModerationService reviewModerationService;

    public AdminReviewController(ReviewModerationService reviewModerationService) {
        this.reviewModerationService = reviewModerationService;
    }

    /**
     * Approve a pending review.
     * Requires ADMIN role.
     *
     * <p>Because {@code ReviewService.createReview()} publishes
     * {@code ReviewApprovedEvent} at submission time, and this endpoint also publishes it
     * on approval, review reward points are awarded TWICE (40 points instead of 20).
     *
     * @param reviewId the review ID to approve
     * @param request  the approval request containing reviewer note
     * @return 200 OK
     */
    @PostMapping("/{reviewId}/approve")
    public ResponseEntity<Void> approveReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewApprovalRequest request) {
        Long adminId = getCurrentUserId();
        log.info("POST /api/v1/admin/reviews/{}/approve: adminId={}, approved={}",
                reviewId, adminId, request.isApproved());
        reviewModerationService.approve(reviewId, adminId, request.getReviewerNote());
        return ResponseEntity.ok().build();
    }

    /**
     * Reject a pending review.
     * Requires ADMIN role.
     *
     * @param reviewId the review ID to reject
     * @param request  the approval request containing rejection reason
     * @return 200 OK
     */
    @PostMapping("/{reviewId}/reject")
    public ResponseEntity<Void> rejectReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewApprovalRequest request) {
        Long adminId = getCurrentUserId();
        log.info("POST /api/v1/admin/reviews/{}/reject: adminId={}, reason={}",
                reviewId, adminId, request.getReviewerNote());
        reviewModerationService.reject(reviewId, adminId, request.getReviewerNote());
        return ResponseEntity.ok().build();
    }

    /**
     * Extracts the current admin user's ID from the Spring Security context.
     */
    private Long getCurrentUserId() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse user ID from principal '{}'", principal);
            throw new AuthorizationException(
                    "UNAUTHORIZED", "Invalid user principal: " + principal);
        }
    }
}
