package com.course.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in for a real mail sender: writes the e-mail to the log. */
@Component
public class LoggingReminderNotifier implements ReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderNotifier.class);

    @Override
    public void remind(String host, String quizTitle) {
        log.info("Email sent to {}: draft '{}' has not been published yet", host, quizTitle);
    }
}
