package com.pipibooks.common

import org.springframework.http.HttpStatus
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64

/** 커서 페이지네이션용 불투명 토큰. 필드 맵을 JSON으로 직렬화해 URL-safe Base64로 인코딩한다. */
object CursorCodec {
    private val mapper = jacksonObjectMapper()
    private val type = object : TypeReference<Map<String, String>>() {}

    fun encode(fields: Map<String, String>): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(fields))

    fun decode(cursor: String): Map<String, String> =
        try {
            mapper.readValue(Base64.getUrlDecoder().decode(cursor), type) ?: throw invalid(null)
        } catch (e: IllegalArgumentException) {
            throw invalid(e)
        } catch (e: tools.jackson.core.JacksonException) {
            throw invalid(e)
        }

    private fun invalid(cause: Exception?): ApiException {
        val e = ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "잘못된 커서")
        cause?.let { e.initCause(it) }
        return e
    }
}
