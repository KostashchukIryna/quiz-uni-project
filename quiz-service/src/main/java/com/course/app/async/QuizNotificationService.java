package com.course.app.async;

import com.course.app.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Slow side effect #1: e-mail to the host. Fire-and-forget: void, nobody waits for it. */
@Service
public class QuizNotificationService {

    private static final Logger log = LoggerFactory.getLogger(QuizNotificationService.class);

    private final SlowWork slowWork;

    public QuizNotificationService(SlowWork slowWork) {
        this.slowWork = slowWork;
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    public void sendQuizCreatedEmail(String recipient, String quizTitle) {
        slowWork.perform("email");
        log.info("Email sent to {}: quiz '{}' was created", recipient, quizTitle);
    }
}
