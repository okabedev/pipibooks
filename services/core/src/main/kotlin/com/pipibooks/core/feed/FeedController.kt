package com.pipibooks.core.feed

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/feed")
class FeedController(
    private val service: FeedService,
) {
    /** 비로그인 피드. `X-Anonymous-Id`는 UUID일 때만 쓰고, 아니면 없는 것으로 본다. */
    @GetMapping
    fun feed(
        @RequestParam(required = false) cursor: String?,
        @RequestHeader("X-Anonymous-Id", required = false) anonymousId: String?,
    ): FeedPage = service.page(Viewer(memberId = null, anonymousId = anonymousId?.takeIf(::isUuid)), cursor)

    private companion object {
        val UUID_FORMAT = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    private fun isUuid(s: String) = UUID_FORMAT.matches(s)
}
