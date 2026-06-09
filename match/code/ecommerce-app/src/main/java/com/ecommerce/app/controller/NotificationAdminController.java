package com.ecommerce.app.controller;

import com.ecommerce.common.notification.NotificationRecordService;
import com.ecommerce.common.notification.NotificationRecordService.NotificationRecordItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdminController.class);

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(required = false) String bizId) {
        log.info("Querying notifications with bizId={}", bizId);

        List<NotificationRecordItem> records;
        if (bizId != null && !bizId.isBlank()) {
            records = NotificationRecordService.getByBizId(bizId);
        } else {
            records = NotificationRecordService.getAll();
        }

        return ResponseEntity.ok(Map.of(
                "count", records.size(),
                "records", records
        ));
    }
}
