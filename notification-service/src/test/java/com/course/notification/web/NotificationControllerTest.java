package com.course.notification.web;

import com.course.common.QuizEvent;
import com.course.notification.config.SecurityConfig;
import com.course.notification.domain.Notification;
import com.course.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    NotificationService service;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void withToken_returnsOnlyTheCallersNotifications() throws Exception {
        UUID quizId = UUID.randomUUID();
        Notification n = new Notification(UUID.randomUUID(), "host1", QuizEvent.Type.QUIZ_PUBLISHED, quizId,
                "Your quiz 'Java' is now published", Instant.parse("2026-11-17T10:00:00Z"),
                Instant.parse("2026-11-17T10:00:05Z"));
        when(service.notificationsFor("host1")).thenReturn(List.of(n));

        mvc.perform(get("/api/notifications").with(jwt().jwt(j -> j.claim("preferred_username", "host1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("QUIZ_PUBLISHED"))
                .andExpect(jsonPath("$[0].quizId").value(quizId.toString()))
                .andExpect(jsonPath("$[0].message").value("Your quiz 'Java' is now published"));

        verify(service).notificationsFor("host1");
    }

    @Test
    void otherUser_getsHisOwnList_notSomeoneElses() throws Exception {
        when(service.notificationsFor("host2")).thenReturn(List.of());

        mvc.perform(get("/api/notifications").with(jwt().jwt(j -> j.claim("preferred_username", "host2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).notificationsFor("host2");
    }
}
