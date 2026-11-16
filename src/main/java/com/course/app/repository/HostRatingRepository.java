package com.course.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.course.app.domain.HostRating;
import com.course.app.domain.HostRatingId;

public interface HostRatingRepository extends JpaRepository<HostRating, HostRatingId> {

    @Modifying
    @Query("delete from HostRating r where r.period = :period")
    int deleteByPeriod(@Param("period") String period);

    List<HostRating> findByPeriodOrderByScoreDescUsernameAsc(String period);
}
