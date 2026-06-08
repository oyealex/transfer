package com.ecommerce.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("MockMailSender")
class MockMailSenderTest {

    private final MockMailSender sender = new MockMailSender();

    @Test
    @DisplayName("sendEmail logs the email parameters without throwing, even with null arguments")
    void testSendEmail_logsSuccessfully() {
        assertThatCode(() -> sender.sendEmail("test@example.com", "Welcome", "Hello, user!"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendEmail accepts null recipient without throwing")
    void testSendEmail_nullRecipient() {
        assertThatCode(() -> sender.sendEmail(null, "Subject", "Body"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendEmail accepts null subject and body without throwing")
    void testSendEmail_nullSubjectAndBody() {
        assertThatCode(() -> sender.sendEmail("test@example.com", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendEmail is a public method on a @Component class, accessible for direct use")
    void testSendEmail_methodIsPubliclyAccessible() throws Exception {
        java.lang.reflect.Method method = MockMailSender.class.getMethod(
                "sendEmail", String.class, String.class, String.class);
        assertThatCode(() -> method.invoke(sender, "a@b.com", "Subject", "Body"))
                .doesNotThrowAnyException();
    }
}
