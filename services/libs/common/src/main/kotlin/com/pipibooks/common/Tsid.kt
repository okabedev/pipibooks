package com.pipibooks.common

import org.springframework.http.HttpStatus
import java.security.SecureRandom

/**
 * 시간순으로 증가하는 64비트 ID(양수).
 * 상위 42비트는 2026-01-01 기준 밀리초, 다음 10비트는 프로세스별 난수, 하위 12비트는 같은 밀리초 안의 순번이다.
 * 외부에는 10진수 문자열로 노출한다.
 */
object Tsid {
    private const val EPOCH_MS = 1_767_225_600_000L // 2026-01-01T00:00:00Z
    private const val NODE_BITS = 10
    private const val SEQ_BITS = 12
    private const val SEQ_MASK = (1L shl SEQ_BITS) - 1

    private val node = SecureRandom().nextInt(1 shl NODE_BITS).toLong()
    private var lastMs = -1L
    private var seq = 0L

    @Synchronized
    fun next(): Long {
        var ms = maxOf(System.currentTimeMillis() - EPOCH_MS, lastMs)
        if (ms == lastMs) {
            seq = (seq + 1) and SEQ_MASK
            // 한 밀리초에 4096개를 넘기면 시각을 앞당겨 단조 증가를 지킨다.
            if (seq == 0L) ms++
        } else {
            seq = 0
        }
        lastMs = ms
        return (ms shl (NODE_BITS + SEQ_BITS)) or (node shl SEQ_BITS) or seq
    }

    fun format(id: Long): String = id.toString()

    fun parse(s: String): Long =
        s.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "잘못된 ID: $s")
}
