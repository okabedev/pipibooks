package com.pipibooks.core.web

import com.pipibooks.common.ApiException
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** [ApiException]을 Problem Details(`application/problem+json`)로 바꾼다. 기계용 오류 코드는 `code` 필드에 담는다. */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(e.status, e.message)
        problem.setProperty("code", e.code)
        return ResponseEntity.status(e.status).body(problem)
    }
}
