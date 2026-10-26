package com.course.app.web;

import com.course.app.async.QuizReportService;
import com.course.app.dto.QuizReport;
import com.course.app.dto.QuizStats;
import com.course.app.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AsyncWebTest {

    @Mock
    private QuizService quizService;
    @Mock
    private QuizReportService reportService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuizApiController(quizService, reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void report_serviceReturnsReport_responds200WithJson() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(reportService.buildReport(id)).thenReturn(
                new QuizReport(id, "Java", 3, true, 120, new QuizStats(true, 7, 10.5)));

        // Act + Assert
        mvc.perform(get("/api/quizzes/{id}/report", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.pdfAvailable").value(true))
                .andExpect(jsonPath("$.stats.plays").value(7));
    }

    @Test
    void report_executorRejectsTask_responds503TryAgainLaterNot500() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(reportService.buildReport(id)).thenThrow(
                new TaskRejectedException("pool exhausted", new RejectedExecutionException()));

        // Act + Assert
        mvc.perform(get("/api/quizzes/{id}/report", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.message").value("The server is busy right now, please try again later"));
    }
}
