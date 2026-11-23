package com.aom.room_reservation.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Returns 401 as an RFC 9457 Problem Details body, consistent with the rest of the API errors
 * (the default Basic entry point only sends the status and the WWW-Authenticate header).
 * Runs in the security filter chain, before Spring MVC, so @RestControllerAdvice cannot handle it.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = """
            {"type":"about:blank","title":"Unauthorized","status":401,\
            "detail":"Valid credentials are required to create or cancel resources (HTTP Basic)"}""";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"room-reservation\"");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
