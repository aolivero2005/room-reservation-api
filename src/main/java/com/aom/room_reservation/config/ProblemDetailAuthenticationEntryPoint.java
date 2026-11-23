package com.aom.room_reservation.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * Sends authentication failures through Spring MVC's exception handling, so the 401 is produced by
 * {@code GlobalExceptionHandler} with the same Problem Details body and serializer as every other
 * error, instead of a hand-written JSON string.
 * <p>
 * Needed because security runs in a servlet filter, before Spring MVC: without this delegation
 * a @RestControllerAdvice never sees authentication errors.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public ProblemDetailAuthenticationEntryPoint(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ModelAndView handled = handlerExceptionResolver.resolveException(request, response, null, authException);
        if (handled == null) {
            // should not happen (GlobalExceptionHandler handles AuthenticationException); never leave it unanswered
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
