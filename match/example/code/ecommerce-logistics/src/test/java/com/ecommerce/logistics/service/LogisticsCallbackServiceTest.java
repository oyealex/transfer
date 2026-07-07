package com.ecommerce.logistics.service;

import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link LogisticsCallbackService}.
 *
 * <p>The {@code processCallback} method only logs the callback data
 * and does NOT update the shipment status or create tracking records in the database.
 * All carrier status updates are silently discarded.
 */
@ExtendWith(MockitoExtension.class)
class LogisticsCallbackServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private LogisticsCallbackService callbackService;

    /**
     * Verifies callback processing interactions.
     */
    @Test
    void testProcessCallback_processesRequest() {
        LogisticsCallbackRequest request = new LogisticsCallbackRequest();
        request.setTrackingNo("TN12345");
        request.setStatus("DELIVERED");
        request.setLocation("Shanghai Distribution Center");
        request.setDescription("Package delivered to recipient");
        request.setEventTime(LocalDateTime.now());
        request.setSignature("RECIPIENT_SIGNATURE");

        callbackService.processCallback(request);

        // The callback is only logged — no DB operations occur.
        // Verify that ShipmentRepository is NEVER used (no save, no findById, etc.)
        verifyNoInteractions(shipmentRepository);
    }

    @Test
    void testProcessCallback_doesNotThrow() {
        LogisticsCallbackRequest request = new LogisticsCallbackRequest();
        request.setTrackingNo("TN99999");
        request.setStatus("IN_TRANSIT");
        request.setLocation("Beijing Sort Center");

        assertDoesNotThrow(() -> callbackService.processCallback(request));

        verifyNoInteractions(shipmentRepository);
    }

    @Test
    void testProcessCallback_nullFields_doesNotThrow() {
        LogisticsCallbackRequest request = new LogisticsCallbackRequest();
        // All fields are null

        assertDoesNotThrow(() -> callbackService.processCallback(request));

        verifyNoInteractions(shipmentRepository);
    }
}
