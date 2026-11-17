package com.course.app.messaging;

import com.course.common.QuizEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Real Spring transaction machinery (a transaction manager that only keeps the books, no database): the event is
 * published after the commit, never before it, and never when the transaction rolls back.
 */
class QuizEventTransactionTest {

    private AnnotationConfigApplicationContext context;
    private RabbitTemplate rabbit;
    private BusinessAction action;
    private BookkeepingTransactionManager transactionManager;
    private Boolean committedWhenSent;

    @BeforeEach
    void startContext() {
        rabbit = mock(RabbitTemplate.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(RabbitTemplate.class, () -> rabbit);
        context.register(Config.class);
        context.refresh();
        action = context.getBean(BusinessAction.class);
        transactionManager = context.getBean(BookkeepingTransactionManager.class);
        // remember whether the transaction had already committed at the moment the event reached the broker
        doAnswer(call -> {
            committedWhenSent = transactionManager.committed;
            return null;
        }).when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void committedAction_publishesTheEventOnce() {
        action.succeed();

        verify(rabbit, times(1)).convertAndSend(any(String.class), any(String.class), any(QuizEvent.class));
    }

    @Test
    void committedAction_publishesOnlyAfterTheCommit_notBeforeIt() {
        action.succeed();

        assertThat(committedWhenSent).as("transaction had committed when the event was sent").isTrue();
    }

    @Test
    void failingAction_rollsBackAndPublishesNothing() {
        assertThatThrownBy(() -> action.fail()).isInstanceOf(IllegalStateException.class);

        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        BookkeepingTransactionManager transactionManager() {
            return new BookkeepingTransactionManager();
        }

        @Bean
        QuizEventPublisher quizEventPublisher(RabbitTemplate rabbitTemplate) {
            return new QuizEventPublisher(rabbitTemplate);
        }

        @Bean
        BusinessAction businessAction(ApplicationEventPublisher events) {
            return new BusinessAction(events);
        }
    }

    /** Stands in for QuizService: raises the event inside its transaction. */
    static class BusinessAction {

        private final ApplicationEventPublisher events;

        BusinessAction(ApplicationEventPublisher events) {
            this.events = events;
        }

        @Transactional
        public void succeed() {
            events.publishEvent(event());
        }

        @Transactional
        public void fail() {
            events.publishEvent(event());
            throw new IllegalStateException("business action failed");
        }

        private static QuizEvent event() {
            return QuizEvent.of(QuizEvent.Type.QUIZ_CREATED, UUID.randomUUID(), "Java", "host1", Instant.now());
        }
    }

    static class BookkeepingTransactionManager extends AbstractPlatformTransactionManager {

        volatile boolean committed;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            committed = false;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
