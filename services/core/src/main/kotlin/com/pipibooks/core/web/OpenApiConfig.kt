package com.pipibooks.core.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** `/v3/api-docs`용 문서 메타데이터(웹·앱 TS 클라이언트 생성의 입력). 문서 UI는 켜지 않는다. */
@Configuration
@EnableScheduling
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(Info().title("pipibooks core API").version("v1"))
}
