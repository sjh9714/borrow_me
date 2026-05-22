package com.ardkyer.borrowme.service;

import com.ardkyer.borrowme.entity.Exercise;
import com.ardkyer.borrowme.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


@Service
public class ExerciseServiceImpl implements ExerciseService{

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Exercise> getAllExercises() {
        return exerciseRepository.findAllWithHashtags();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getHashtagsByExerciseName(String name) {
        return exerciseRepository.findHashtagsByExerciseName(name);
    }

}
