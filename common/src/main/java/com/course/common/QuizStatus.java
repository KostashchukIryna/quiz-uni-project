package com.course.common;

/** Lifecycle of a quiz. Shared because quiz-service stores it and scheduler-service filters on the stored value. */
public enum QuizStatus { DRAFT, PUBLISHED }
