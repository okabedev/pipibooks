package com.pipibooks.common

import org.springframework.http.HttpStatus

/**
 * API 오류. core의 `@RestControllerAdvice`가 Problem Details(`application/problem+json`)로 바꾸며,
 * [code]는 클라이언트가 분기하는 기계용 오류 코드(`X_NOT_FOUND` 형태)다.
 */
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)
