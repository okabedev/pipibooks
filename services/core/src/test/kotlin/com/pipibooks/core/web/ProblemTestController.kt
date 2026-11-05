package com.pipibooks.core.web

import com.pipibooks.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// ApiExceptionHandler 검증용. 테스트 클래스패스에만 있다.
@RestController
class ProblemTestController {
    @GetMapping("/test/api-exception")
    fun fail(): Nothing = throw ApiException(HttpStatus.NOT_FOUND, "X_NOT_FOUND", "없음")
}
