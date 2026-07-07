package com.ecommerce.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalNotificationServiceImpl")
class LocalNotificationServiceImplTest {

    @Mock
    private MockMailSender mockMailSender;

    @Mock
    private MockSmsSender mockSmsSender;

    @InjectMocks
    private LocalNotificationServiceImpl service;

    @Test
    @DisplayName("sends notification only once when duplicate requests share the same idempotency key")
    void testSend_notificationWithIdempotencyKey_onlySentOnce() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-001")
                .receiver("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("ORDER_CONFIRMATION")
                .idempotencyKey("idem-key-123")
                .build();

        service.send(request);
        service.send(request);
        service.send(request);

        verify(mockMailSender, times(1))
                .sendEmail(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("sends notification separately for requests with different idempotency keys")
    void testSend_differentKeys_sendSeparately() {
        NotificationRequest request1 = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-001")
                .receiver("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("TEMPLATE_1")
                .idempotencyKey("key-A")
                .build();

        NotificationRequest request2 = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-002")
                .receiver("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("TEMPLATE_2")
                .idempotencyKey("key-B")
                .build();

        service.send(request1);
        service.send(request2);

        verify(mockMailSender, times(2))
                .sendEmail(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("delegates to MockMailSender for EMAIL channel")
    void testSend_delegatesToMockMailSenderForEmail() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-003")
                .receiver("customer@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("ORDER_SHIPPED")
                .build();

        service.send(request);

        verify(mockMailSender).sendEmail(
                eq("customer@example.com"),
                eq("[ORDER] Notification"),
                anyString());
        verifyNoInteractions(mockSmsSender);
    }

    @Test
    @DisplayName("delegates to MockSmsSender for SMS channel")
    void testSend_delegatesToMockSmsSenderForSms() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("PROMO")
                .bizId("PROMO-001")
                .receiver("+1234567890")
                .channel(NotificationChannel.SMS)
                .templateCode("PROMO_OFFER")
                .build();

        service.send(request);

        verify(mockSmsSender).sendSms(eq("+1234567890"), anyString());
        verifyNoInteractions(mockMailSender);
    }

    @Test
    @DisplayName("handles IN_APP channel by logging without delegating to mail or SMS senders")
    void testSend_inAppChannel_logsButUsesNoSender() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("SYSTEM")
                .bizId("SYS-001")
                .receiver("user123")
                .channel(NotificationChannel.IN_APP)
                .templateCode("WELCOME")
                .build();

        service.send(request);

        verifyNoInteractions(mockMailSender, mockSmsSender);
    }

    @Test
    @DisplayName("ignores null NotificationRequest without throwing exception")
    void testSend_nullRequest_isIgnoredSafely() {
        service.send(null);

        verifyNoInteractions(mockMailSender, mockSmsSender);
    }

    @Test
    @DisplayName("request without idempotency key is sent every time it is called")
    void testSend_noIdempotencyKey_sendsEveryTime() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-005")
                .receiver("repeat@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("ALERT")
                .build();

        service.send(request);
        service.send(request);

        verify(mockMailSender, times(2))
                .sendEmail(eq("repeat@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("notification for EMAIL channel uses subject format [bizType] Notification")
    void testSend_emailSubjectContainsBizType() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("PAYMENT")
                .bizId("PAY-100")
                .receiver("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("PAYMENT_RECEIVED")
                .build();

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        service.send(request);

        verify(mockMailSender).sendEmail(anyString(), subjectCaptor.capture(), anyString());
        assertThat(subjectCaptor.getValue()).isEqualTo("[PAYMENT] Notification");
    }

    @Test
    @DisplayName("rendered template body includes template code and variables for EMAIL channel")
    void testSend_templateBodyIncludesTemplateCodeAndVariables() {
        NotificationRequest request = NotificationRequest.builder()
                .bizType("ORDER")
                .bizId("ORD-007")
                .receiver("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .templateCode("ORDER_CONFIRMATION")
                .variables(java.util.Map.of("orderId", "ORD-007", "amount", "99.99"))
                .build();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        service.send(request);

        verify(mockMailSender).sendEmail(anyString(), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertThat(body).startsWith("[ORDER_CONFIRMATION]");
        assertThat(body).contains("orderId");
        assertThat(body).contains("99.99");
    }
}
