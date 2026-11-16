package com.course.app.mapper;

import com.course.app.domain.Quiz;
import com.course.app.dto.QuizDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = QuestionMapper.class)
public interface QuizMapper {

    QuizDto toDto(Quiz quiz);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Quiz toEntity(QuizDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(QuizDto dto, @MappingTarget Quiz quiz);

    /** The generated update() fills the existing collection in place, so restore the back-reference. */
    @AfterMapping
    default void linkQuestions(@MappingTarget Quiz quiz) {
        quiz.getQuestions().forEach(q -> q.setQuiz(quiz));
    }
}
