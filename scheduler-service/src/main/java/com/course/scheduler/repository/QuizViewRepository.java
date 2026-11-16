package com.course.scheduler.repository;

import com.course.scheduler.domain.QuizView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only access to the quiz data owned by quiz-service. */
public interface QuizViewRepository extends JpaRepository<QuizView, UUID> {

    /** Per-host activity since {@code since}; the rating job turns it into a score. */
    @Query("""
            select q.createdBy as username,
                   count(distinct q.id) as quizzes,
                   count(distinct case when q.status = com.course.common.QuizStatus.PUBLISHED then q.id end) as published,
                   count(qn.id) as questions
            from QuizView q left join QuestionView qn on qn.quizId = q.id
            where q.createdAt >= :since
            group by q.createdBy
            """)
    List<HostActivity> summarizeByHost(@Param("since") Instant since);

    /** Drafts created before {@code cutoff} whose host has not been reminded yet. */
    @Query("""
            select q from QuizView q
            where q.status = com.course.common.QuizStatus.DRAFT
              and q.createdAt < :cutoff
              and not exists (select 1 from DraftReminder r where r.quizId = q.id)
            """)
    List<QuizView> findStaleDrafts(@Param("cutoff") Instant cutoff);

    interface HostActivity {
        String getUsername();

        long getQuizzes();

        long getPublished();

        long getQuestions();
    }
}
