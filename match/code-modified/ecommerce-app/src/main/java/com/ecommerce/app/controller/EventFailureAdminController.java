package com.ecommerce.app.controller;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/events")
public class EventFailureAdminController {

    private static final Logger log = LoggerFactory.getLogger(EventFailureAdminController.class);

    private final FailedEventRecordRepository failedEventRecordRepository;
    private final DomainEventPublisher domainEventPublisher;

    public EventFailureAdminController(FailedEventRecordRepository failedEventRecordRepository,
                                       DomainEventPublisher domainEventPublisher) {
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @GetMapping("/failures")
    public ResponseEntity<Map<String, Object>> getFailures(
            @RequestParam(required = false) String eventType) {
        log.info("Querying event failures, eventType={}", eventType);

        List<FailedEventRecord> records;
        if (eventType != null && !eventType.isBlank()) {
            records = failedEventRecordRepository.findAll().stream()
                    .filter(r -> eventType.equals(r.getEventType()) || eventType.equals(r.getEventType().substring(r.getEventType().lastIndexOf('.') + 1)))
                    .toList();
        } else {
            records = failedEventRecordRepository.findAll();
        }

        return ResponseEntity.ok(Map.of(
                "count", records.size(),
                "records", records
        ));
    }

    @PostMapping("/failures/replay")
    public ResponseEntity<Map<String, Object>> replayFailures() {
        log.info("Replaying all failed events");
        int replayed = domainEventPublisher.replayAll();
        return ResponseEntity.ok(Map.of(
                "replayed", replayed
        ));
    }
}
