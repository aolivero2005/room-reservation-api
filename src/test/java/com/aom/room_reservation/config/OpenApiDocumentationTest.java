package com.aom.room_reservation.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated OpenAPI spec is public and describes what the API really does:
 * which operations need credentials, which status codes they return and the error body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final String PROBLEM_REF = "#/components/schemas/ApiProblem";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void specIsPublicAndDeclaresBasicAuth() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.info.title").value("Room Reservation API"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));
    }

    @Test
    void writeOperationsRequireCredentialsAndReadOperationsDoNot() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/rooms'].post.security[0]", hasKey("basicAuth")))
                .andExpect(jsonPath("$.paths['/api/reservations'].post.security[0]", hasKey("basicAuth")))
                .andExpect(jsonPath("$.paths['/api/reservations/{id}'].delete.security[0]", hasKey("basicAuth")))
                .andExpect(jsonPath("$.paths['/api/rooms'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/reservations'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/reservations/{id}'].get.security").doesNotExist());
    }

    @Test
    void createReservationDocumentsItsRealStatusCodes() throws Exception {
        String responses = "$.paths['/api/reservations'].post.responses";
        apiDocs()
                .andExpect(jsonPath(responses, hasKey("201")))
                .andExpect(jsonPath(responses, hasKey("400")))
                .andExpect(jsonPath(responses, hasKey("401")))
                .andExpect(jsonPath(responses, hasKey("404")))
                .andExpect(jsonPath(responses, hasKey("409")))
                .andExpect(jsonPath(responses, not(hasKey("200"))))
                .andExpect(jsonPath(responses + "['201'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/ReservationResponse"))
                .andExpect(jsonPath(responses + "['409'].content['application/problem+json'].schema.$ref")
                        .value(PROBLEM_REF));
    }

    @Test
    void cancelDocuments204AndErrors() throws Exception {
        String responses = "$.paths['/api/reservations/{id}'].delete.responses";
        apiDocs()
                .andExpect(jsonPath(responses, hasKey("204")))
                .andExpect(jsonPath(responses, hasKey("401")))
                .andExpect(jsonPath(responses, hasKey("404")))
                .andExpect(jsonPath(responses, hasKey("409")))
                .andExpect(jsonPath(responses, not(hasKey("200"))));
    }

    @Test
    void listDocumentsFiltersAndPaginationAsSeparateQueryParameters() throws Exception {
        String params = "$.paths['/api/reservations'].get.parameters[*].name";
        apiDocs()
                .andExpect(jsonPath(params, org.hamcrest.Matchers.hasItems(
                        "roomId", "date", "zone", "status", "page", "size", "sort")));
    }

    @Test
    void problemSchemaDescribesValidationErrors() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties", hasKey("detail")))
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties.errors.type").value("array"));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }
}
