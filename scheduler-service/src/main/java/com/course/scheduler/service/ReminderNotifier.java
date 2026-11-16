package com.course.scheduler.service;

/** Tells a host that a draft is still unpublished. */
public interface ReminderNotifier {

    void remind(String host, String quizTitle);
}
