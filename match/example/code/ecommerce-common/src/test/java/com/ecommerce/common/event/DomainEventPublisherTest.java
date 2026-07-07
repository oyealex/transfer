package com.ecommerce.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DomainEventPublisher")
class DomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DomainEventPublisher publisher;

    /**
     * Concrete domain event for testing.
     */
    static class TestDomainEvent extends AbstractDomainEvent {
        private final String data;

        public TestDomainEvent(Object source, String data) {
            super(source);
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    @Test
    @DisplayName("publishes event by delegating to Spring ApplicationEventPublisher")
    void testPublishEvent_successfullyCallsApplicationEventPublisher() {
        TestDomainEvent event = new TestDomainEvent(this, "hello");

        publisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("catches and swallows exception thrown during event publication, never rethrows")
    void testListenerThrowsException_logsErrorButDoesNotThrow() {
        TestDomainEvent event = new TestDomainEvent(this, "will-fail");
        doThrow(new RuntimeException("Simulated listener failure"))
                .when(applicationEventPublisher).publishEvent(any(TestDomainEvent.class));

        assertThatCode(() -> publisher.publish(event))
                .doesNotThrowAnyException();

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    @DisplayName("persists FailedEventRecord when publication fails")
    void testFailedEventRecord_persistedOnFailure() throws Exception {
        FailedEventRecord record = new FailedEventRecord();
        record.setEventType("TestEvent");
        record.setEventPayload("{\"data\":\"test\"}");
        record.setErrorMessage("Something went wrong");
        record.setOccurredAt(java.time.LocalDateTime.now());
        record.setRetried(false);
        record.setRetryCount(0);

        assertThat(record.getEventType()).isEqualTo("TestEvent");
        assertThat(record.getEventPayload()).isEqualTo("{\"data\":\"test\"}");
        assertThat(record.getErrorMessage()).isEqualTo("Something went wrong");
        assertThat(record.getOccurredAt()).isNotNull();
        assertThat(record.isRetried()).isFalse();
        assertThat(record.getRetryCount()).isZero();

        Constructor<?>[] constructors = DomainEventPublisher.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterCount()).isEqualTo(3);
        assertThat(constructors[0].getParameterTypes()[0])
                .isEqualTo(ApplicationEventPublisher.class);
        assertThat(constructors[0].getParameterTypes()[1])
                .isEqualTo(FailedEventRecordRepository.class);
        assertThat(constructors[0].getParameterTypes()[2])
                .isEqualTo(ObjectMapper.class);

        boolean hasFailedEventRepositoryField = false;
        for (Field field : DomainEventPublisher.class.getDeclaredFields()) {
            if (field.getType().equals(FailedEventRecordRepository.class)) {
                hasFailedEventRepositoryField = true;
            }
        }
        assertThat(hasFailedEventRepositoryField).isTrue();

        TestDomainEvent event = new TestDomainEvent(this, "orphaned");
        doThrow(new RuntimeException("Listener crash"))
                .when(applicationEventPublisher).publishEvent(any(TestDomainEvent.class));
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"data\":\"orphaned\"}");

        assertThatCode(() -> publisher.publish(event))
                .doesNotThrowAnyException();

        verify(failedEventRecordRepository).save(any(FailedEventRecord.class));
    }
}
