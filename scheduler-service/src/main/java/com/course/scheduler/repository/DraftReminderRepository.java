package com.course.scheduler.repository;

import com.course.scheduler.domain.DraftReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftReminderRepository extends JpaRepository<DraftReminder, UUID> {
}
