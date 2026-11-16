package com.course.app.mapper;

import com.course.app.domain.Question;
import com.course.app.dto.QuestionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuestionMapper {

    QuestionDto toDto(Question question);

    @Mapping(target = "id", ignore = true) // questions are always (re)created from the payload
    @Mapping(target = "quiz", ignore = true)
    Question toEntity(QuestionDto dto);
}
