package com.course.app.repository;

import com.course.app.domain.Quiz;
import com.course.app.domain.QuizStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    @Query("""
            select q.createdBy as username,
                   count(distinct q.id) as quizzes,
                   count(distinct case when q.status = com.course.app.domain.QuizStatus.PUBLISHED then q.id end) as published,
                   count(qn.id) as questions
            from Quiz q left join q.questions qn
            where q.createdAt >= :since
            group by q.createdBy
            """)
    List<HostActivity> summarizeByHost(@Param("since") Instant since);

    List<Quiz> findByStatusAndRemindedAtIsNullAndCreatedAtBefore(QuizStatus status, Instant cutoff);

    interface HostActivity {
        String getUsername();

        long getQuizzes();

        long getPublished();

        long getQuestions();
    }
}
