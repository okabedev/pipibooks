package com.pipibooks.core.web

import com.pipibooks.core.support.DbTestSupport
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest(
    @Autowired private val mockMvc: MockMvc,
) : DbTestSupport() {
    @Test
    fun `api-docs는 카드 조회 경로를 노출한다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/v1/cards/{id}']") { exists() }
        }
    }

    @Test
    fun `슬라이드 스키마는 type 구분자와 슬라이드 종류별 oneOf를 노출한다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.schemas.SlidePayload.discriminator.propertyName") { value("type") }
            jsonPath("$.components.schemas.SlidePayload.required[0]") { value("type") }
            jsonPath("$.components.schemas.CardView.properties.slides.items.oneOf.length()") { value(6) }
            jsonPath("$.components.schemas.CardView.properties.slides.items.oneOf[*]['\$ref']") {
                value(hasItem("#/components/schemas/Cover"))
            }
        }
    }
}
