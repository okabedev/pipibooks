package com.pipibooks.catalog.collect

import com.pipibooks.catalog.health.Heartbeat
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 매일 04:00 KST에 신간을 수집한다. 인증키가 없어도 앱은 죽지 않고 실행이 FAILED로 남는다. */
@Component
class CollectScheduler(
    private val service: CollectService,
    private val heartbeat: Heartbeat,
) {
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun collectNewBooks() {
        // 시작만 블로킹으로 기다리고 수집 자체는 백그라운드에서 돈다.
        runBlocking { service.launch(CollectRequest(CollectType.NEW_BOOKS), heartbeat::report) }
    }
}
