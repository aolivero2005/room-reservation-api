package com.aom.room_reservation.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An AccessDeniedException thrown inside a controller (what @PreAuthorize would do) must reach
 * Spring Security instead of being swallowed by the catch-all handler of GlobalExceptionHandler:
 * 401 for an anonymous user, 403 for an authenticated one, never 500.
 */
@WebMvcTest(controllers = AccessDeniedHandlingTest.ForbiddenController.class)
@Import({SecurityConfig.class, AccessDeniedHandlingTest.ForbiddenController.class})
class AccessDeniedHandlingTest {

    /** Test-only endpoint under a public GET path, so the request reaches the controller. */
    @RestController
    static class ForbiddenController {
        @GetMapping("/api/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("not allowed");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUserGets401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void authenticatedUserGets403NotA500() throws Exception {
        mockMvc.perform(get("/api/test/forbidden").with(user("test-user")))
                .andExpect(status().isForbidden());
    }
}
