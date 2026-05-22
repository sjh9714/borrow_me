package com.ardkyer.borrowme.repository;

import com.ardkyer.borrowme.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    Exercise findByName(String name);

    @Query("SELECT DISTINCT e FROM Exercise e LEFT JOIN FETCH e.hashtags")
    List<Exercise> findAllWithHashtags();

    @Query("SELECT h.name FROM Exercise e JOIN e.hashtags h WHERE e.name = :name")
    Set<String> findHashtagsByExerciseName(@Param("name") String name);
}
