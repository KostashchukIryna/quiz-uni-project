package com.course.app.web;

import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import com.course.app.web.annotation.PostCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc in standalone mode: real MVC pipeline (routing, resolvers, validation), no Spring context. */
@ExtendWith(MockitoExtension.class)
class CustomAnnotationsWebTest {

    private static final String VALID = """
            {"title":"Java","status":"DRAFT","questions":[
              {"text":"q?","timeLimitSeconds":15,"points":10,"correctOption":1,"options":["a","b"]}]}""";
    private static final String BAD_ANSWER = VALID.replace("\"correctOption\":1", "\"correctOption\":5");

    @RestController
    @RequestMapping("/composed")
    static class ComposedController {
        @PostCreated("/items")
        String create(@RequestBody String body) {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/original")
    static class OriginalController {
        @RequestMapping(path = "/items", method = RequestMethod.POST)
        @ResponseStatus(HttpStatus.CREATED)
        String create(@RequestBody String body) {
            return "ok";
        }
    }

    @Mock
    private QuizService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new QuizApiController(service), new ComposedController(), new OriginalController())
                .setCustomArgumentResolvers(new CurrentUsernameArgumentResolver())
                .build();
    }

    // ---- composed annotation: same behaviour as the two original annotations (AC4, AC5) ----

    @Test
    void composedAnnotation_post_behavesLikeOriginalMappingPlusResponseStatus() throws Exception {
        // Act
        var composed = mvc.perform(post("/composed/items").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        var original = mvc.perform(post("/original/items").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        // Assert
        assertThat(composed.getResponse().getStatus()).isEqualTo(201).isEqualTo(original.getResponse().getStatus());
        assertThat(composed.getResponse().getContentAsString()).isEqualTo(original.getResponse().getContentAsString());
    }

    @Test
    void composedAnnotation_otherHttpMethod_isNotRouted() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/composed/items"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createQuiz_validRequest_returns201() throws Exception {
        // Arrange
        when(service.create(any(), eq("host1"))).thenReturn(
                new QuizDto(UUID.randomUUID(), "Java", null, QuizStatus.DRAFT, "host1", null, List.of()));

        // Act + Assert
        mvc.perform(post("/api/quizzes").principal(() -> "host1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("host1"));
    }

    // ---- argument resolver: value really used inside the controller method (AC8) ----

    @Test
    void createQuiz_authenticatedUser_resolvedUsernameIsPassedToService() throws Exception {
        // Arrange
        when(service.create(any(), any())).thenReturn(
                new QuizDto(UUID.randomUUID(), "Java", null, QuizStatus.DRAFT, "alice", null, List.of()));

        // Act
        mvc.perform(post("/api/quizzes").principal(() -> "alice")
                .contentType(MediaType.APPLICATION_JSON).content(VALID));

        // Assert
        ArgumentCaptor<String> username = ArgumentCaptor.forClass(String.class);
        verify(service).create(any(QuizDto.class), username.capture());
        assertThat(username.getValue()).isEqualTo("alice");
    }

    @Test
    void createQuiz_noPrincipal_returns401AndDoesNotCallService() throws Exception {
        mvc.perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verify(service, never()).create(any(), any());
    }

    // ---- bean validation through a real request (AC2, AC3) ----

    @Test
    void createQuiz_correctOptionOutOfRange_returns400WithExplicitMessage() throws Exception {
        // Act
        var result = mvc.perform(post("/api/quizzes").principal(() -> "host1")
                        .contentType(MediaType.APPLICATION_JSON).content(BAD_ANSWER))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Assert
        assertThat(result.getResolvedException()).hasMessageContaining("correctOption 5 is out of range");
        verify(service, never()).create(any(), any());
    }
}
