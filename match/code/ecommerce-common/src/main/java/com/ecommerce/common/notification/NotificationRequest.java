package com.ecommerce.common.notification;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for sending a notification through the LocalNotificationService.
 * All business modules must use this DTO — never call MockMailSender or MockSmsSender directly.
 */
public class NotificationRequest {

    private String bizType;
    private String bizId;
    private String receiver;
    private NotificationChannel channel;
    private String templateCode;
    private Map<String, Object> variables;
    private String idempotencyKey;

    // Legacy fields used by some module builders
    private String subject;
    private String content;

    public NotificationRequest() {
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

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getVariablesOrDefault() {
        return variables != null ? variables : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bizType;
        private String bizId;
        private String receiver;
        private NotificationChannel channel;
        private String templateCode;
        private Map<String, Object> variables;
        private String idempotencyKey;
        private String subject;
        private String content;

        public Builder bizType(String bizType) { this.bizType = bizType; return this; }
        public Builder bizId(String bizId) { this.bizId = bizId; return this; }
        public Builder receiver(String receiver) { this.receiver = receiver; return this; }
        public Builder recipient(String recipient) { this.receiver = recipient; return this; }
        public Builder channel(NotificationChannel channel) { this.channel = channel; return this; }
        public Builder templateCode(String templateCode) { this.templateCode = templateCode; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder content(String content) { this.content = content; return this; }

        public NotificationRequest build() {
            NotificationRequest req = new NotificationRequest();
            req.setBizType(this.bizType);
            req.setBizId(this.bizId);
            req.setReceiver(this.receiver);
            req.setChannel(this.channel);
            req.setTemplateCode(this.templateCode);
            req.setVariables(this.variables);
            req.setIdempotencyKey(this.idempotencyKey);
            req.setSubject(this.subject);
            req.setContent(this.content);
            return req;
        }
    }
}
