package com.ecommerce.review.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.review.entity.Review;
import com.ecommerce.review.entity.ReviewStatus;
import com.ecommerce.review.event.ReviewApprovedEvent;
import com.ecommerce.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewModerationService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewModerationService")
class ReviewModerationServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private ReviewModerationService moderationService;

    private Review pendingReview;

    @BeforeEach
    void setUp() {
        pendingReview = new Review();
        pendingReview.setId(10L);
        pendingReview.setUserId(1L);
        pendingReview.setProductId(100L);
        pendingReview.setRating(4);
        pendingReview.setContent("Test review content");
        pendingReview.setStatus(ReviewStatus.PENDING_REVIEW);
    }

    // -----------------------------------------------------------------------
    // Approve tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("approve")
    class ApproveTests {

        @Test
        @DisplayName("approve: sets status to APPROVED, records admin info, and publishes ReviewApprovedEvent")
        void testApprove_setsStatusApproved_andPublishesEvent() {
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            moderationService.approve(10L, 99L, "Looks good");

            assertThat(pendingReview.getStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(pendingReview.getReviewedBy()).isEqualTo(99L);
            assertThat(pendingReview.getReviewedAt()).isNotNull();
            assertThat(pendingReview.getReviewerResponse()).isEqualTo("Looks good");

            verify(reviewRepository).save(pendingReview);

            // This is the SECOND time ReviewApprovedEvent
            // Verify approval event publication.
            verify(eventPublisher).publish(any(ReviewApprovedEvent.class));
        }

        @Test
        @DisplayName("approve: does not overwrite reviewerResponse when note is null")
        void testApprove_nullNote_doesNotSetResponse() {
            pendingReview.setReviewerResponse(null);
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            moderationService.approve(10L, 99L, null);

            assertThat(pendingReview.getReviewerResponse()).isNull();
            assertThat(pendingReview.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        }

        @Test
        @DisplayName("approve: does not overwrite reviewerResponse when note is blank")
        void testApprove_blankNote_doesNotSetResponse() {
            pendingReview.setReviewerResponse(null);
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            moderationService.approve(10L, 99L, "   ");

            assertThat(pendingReview.getReviewerResponse()).isNull();
        }

        @Test
        @DisplayName("approve: throws when review is not PENDING_REVIEW")
        void testApprove_nonPending_throws() {
            pendingReview.setStatus(ReviewStatus.APPROVED);
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));

            assertThatThrownBy(() -> moderationService.approve(10L, 99L, "note"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only PENDING_REVIEW reviews can be approved");
        }

        @Test
        @DisplayName("approve: throws when review not found")
        void testApprove_notFound_throws() {
            when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> moderationService.approve(999L, 99L, "note"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Reject tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("reject")
    class RejectTests {

        @Test
        @DisplayName("reject: sets status to REJECTED with reviewer note")
        void testReject_setsStatusRejected() {
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            moderationService.reject(10L, 99L, "Inappropriate content");

            assertThat(pendingReview.getStatus()).isEqualTo(ReviewStatus.REJECTED);
            assertThat(pendingReview.getReviewedBy()).isEqualTo(99L);
            assertThat(pendingReview.getReviewedAt()).isNotNull();
            assertThat(pendingReview.getReviewerResponse()).isEqualTo("Inappropriate content");

            verify(reviewRepository).save(pendingReview);
        }

        @Test
        @DisplayName("reject: throws when review is not PENDING_REVIEW")
        void testReject_nonPending_throws() {
            pendingReview.setStatus(ReviewStatus.APPROVED);
            when(reviewRepository.findById(10L)).thenReturn(Optional.of(pendingReview));

            assertThatThrownBy(() -> moderationService.reject(10L, 99L, "reason"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only PENDING_REVIEW reviews can be rejected");
        }
    }
}
