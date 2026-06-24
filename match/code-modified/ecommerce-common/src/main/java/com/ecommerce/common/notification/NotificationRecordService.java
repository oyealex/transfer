package com.ecommerce.common.notification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NotificationRecordService {
    private static final List<NotificationRecordItem> records = new CopyOnWriteArrayList<>();
    private NotificationRecordService() {}

    public static void record(String bizType, String bizId, String receiver,
                              NotificationChannel channel, String templateCode, String idempotencyKey) {
        records.add(new NotificationRecordItem(bizType, bizId, receiver, channel, templateCode, idempotencyKey, LocalDateTime.now()));
    }

    public static List<NotificationRecordItem> getAll() { return new ArrayList<>(records); }
    public static List<NotificationRecordItem> getByBizId(String bizId) {
        return records.stream().filter(r -> r.getBizId().equals(bizId)).toList();
    }
    public static void clear() { records.clear(); }

    public static class NotificationRecordItem {
        private final String bizType, bizId, receiver;
        private final NotificationChannel channel;
        private final String templateCode, idempotencyKey;
        private final LocalDateTime sentAt;
        public NotificationRecordItem(String bt, String bi, String r, NotificationChannel c, String tc, String ik, LocalDateTime sa) {
            this.bizType = bt; this.bizId = bi; this.receiver = r; this.channel = c;
            this.templateCode = tc; this.idempotencyKey = ik; this.sentAt = sa;
        }
        public String getBizType() { return bizType; }
        public String getBizId() { return bizId; }
        public String getReceiver() { return receiver; }
        public NotificationChannel getChannel() { return channel; }
        public String getTemplateCode() { return templateCode; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public LocalDateTime getSentAt() { return sentAt; }
    }
}
