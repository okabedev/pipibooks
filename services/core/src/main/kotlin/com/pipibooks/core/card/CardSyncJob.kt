package com.pipibooks.core.card

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 5분마다 catalog의 새 카드 초안을 core 카드로 동기화한다. `card.sync.enabled=false`면 등록하지 않는다. */
@Component
@ConditionalOnProperty(name = ["card.sync.enabled"], havingValue = "true", matchIfMissing = true)
class CardSyncJob(
    private val service: CardService,
) {
    @Scheduled(fixedDelay = 300_000)
    fun run() {
        service.syncDrafts()
    }
}
