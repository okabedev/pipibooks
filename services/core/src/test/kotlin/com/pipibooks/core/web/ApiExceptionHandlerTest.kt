package com.pipibooks.core.web

import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) : DbTestSupport() {
    @Test
    fun `ApiException은 Problem Details로 응답한다`() {
        mockMvc.get("/test/api-exception").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.code") { value("X_NOT_FOUND") }
            jsonPath("$.detail") { value("없음") }
        }
    }
}
