package com.aom.room_reservation.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * OpenAPI 3 description of the API, served at /v3/api-docs and browsable at /swagger-ui.html.
 * <p>
 * Controllers only declare which status codes each operation can return. This class adds what is
 * common to all of them, so it is not repeated on every endpoint:
 * <ul>
 *   <li>every 4xx/5xx response gets the RFC 9457 Problem Details body ({@code ApiProblem} schema);</li>
 *   <li>every operation that requires credentials gets the 401 response.</li>
 * </ul>
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Room Reservation API",
        version = "v1",
        description = """
                Book meeting rooms. Read endpoints are public; creating or cancelling requires \
                HTTP Basic credentials (default admin / admin123). Times are ISO-8601: requests must \
                include an offset (e.g. -03:00 or Z), responses are always in UTC. Errors follow \
                RFC 9457 (application/problem+json)."""))
@SecurityScheme(name = OpenApiConfig.BASIC_AUTH, type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {

    public static final String BASIC_AUTH = "basicAuth";

    private static final String PROBLEM_SCHEMA = "ApiProblem";
    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
    private static final String ANY_MEDIA_TYPE = "*/*";

    static {
        // springdoc hides ZoneId parameters by default because Spring MVC can resolve a ZoneId from the
        // request itself; ours is a real @RequestParam ("zone" filter), so it must appear in the spec.
        SpringDocUtils.getConfig().removeRequestWrapperToIgnore(ZoneId.class);
    }

    @Bean
    OpenApiCustomizer problemDetailsCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (requiresAuthentication(operation)) {
                    operation.getResponses().addApiResponse("401",
                            new ApiResponse().description("Missing or invalid credentials"));
                }
                fixResponseBodies(operation.getResponses());
            }));
        };
    }

    private static boolean requiresAuthentication(Operation operation) {
        return operation.getSecurity() != null && !operation.getSecurity().isEmpty();
    }

    /**
     * springdoc gives every declared response the method's return type as body ("*&#47;*"), which is
     * wrong for errors (a 409 does not return a ReservationResponse). Errors always get the Problem
     * Details body; successful bodies are labelled application/json, which is what the API produces.
     */
    private static void fixResponseBodies(ApiResponses responses) {
        responses.forEach((code, response) -> {
            boolean isError = code.startsWith("4") || code.startsWith("5");
            if (isError) {
                response.setContent(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
            } else if (response.getContent() != null && response.getContent().containsKey(ANY_MEDIA_TYPE)) {
                MediaType body = response.getContent().remove(ANY_MEDIA_TYPE);
                response.getContent().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, body);
            }
        });
    }

    /** The shape GlobalExceptionHandler actually returns (ProblemDetail + our "errors" extension). */
    @SuppressWarnings("rawtypes")
    private static Schema problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("end"))
                .addProperty("message", new StringSchema().example("end must be after start"));

        return new ObjectSchema()
                .description("RFC 9457 Problem Details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("The request contains invalid fields"))
                .addProperty("instance", new StringSchema())
                .addProperty("errors", new ArraySchema().items(fieldError)
                        .description("Only on validation errors: one entry per invalid field"));
    }
}
