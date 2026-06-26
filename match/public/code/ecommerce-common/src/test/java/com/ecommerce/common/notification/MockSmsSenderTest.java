package com.ecommerce.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("MockSmsSender")
class MockSmsSenderTest {

    private final MockSmsSender sender = new MockSmsSender();

    @Test
    @DisplayName("sendSms logs the SMS parameters without throwing")
    void testSendSms_logsSuccessfully() {
        assertThatCode(() -> sender.sendSms("+1234567890", "Your OTP is 123456"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendSms accepts null phone number without throwing")
    void testSendSms_nullPhone() {
        assertThatCode(() -> sender.sendSms(null, "Some content"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendSms accepts null content without throwing")
    void testSendSms_nullContent() {
        assertThatCode(() -> sender.sendSms("+1234567890", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendSms is a public method on a @Component class, accessible for direct use")
    void testSendSms_methodIsPubliclyAccessible() throws Exception {
        java.lang.reflect.Method method = MockSmsSender.class.getMethod(
                "sendSms", String.class, String.class);
        assertThatCode(() -> method.invoke(sender, "+1111111111", "Test SMS"))
                .doesNotThrowAnyException();
    }
}
