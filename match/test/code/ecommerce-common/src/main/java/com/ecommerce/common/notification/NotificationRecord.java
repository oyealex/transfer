package com.ecommerce.common.notification;

import java.time.Instant;

/**
 * Record DTO capturing each notification sent through LocalNotificationServiceImpl
 * for test observation purposes.
 */
public class NotificationRecord {

    private String bizType;
    private String bizId;
    private String receiver;
    private String channel;
    private String templateCode;
    private String idempotencyKey;
    private Instant sentAt;

    public NotificationRecord() {
    }

    public NotificationRecord(String bizType, String bizId, String receiver,
                               String channel, String templateCode,
                               String idempotencyKey, Instant sentAt) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.receiver = receiver;
        this.channel = channel;
        this.templateCode = templateCode;
        this.idempotencyKey = idempotencyKey;
        this.sentAt = sentAt;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
