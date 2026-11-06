package com.pipibooks.core.feed

import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest(properties = ["feed.weights.random=0", "feed.page-size=5", "feed.session-ttl=10m"])
class FeedPropertiesTest(
    @Autowired private val props: FeedProperties,
) : DbTestSupport() {
    @Test
    fun `설정값이 바인딩되고 나머지는 기본값이다`() {
        assertEquals(0.0, props.weights.random)
        assertEquals(5, props.pageSize)
        assertEquals(Duration.ofMinutes(10), props.sessionTtl)
        assertEquals(3.0, props.weights.interest)
        assertEquals(14, props.freshnessHalfLifeDays)
        assertEquals(2000, props.candidateLimit)
    }
}
