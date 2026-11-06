package com.pipibooks.core.card

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "card", schema = "core")
class CardEntity(
    @Id
    var id: Long = 0,
    @Column(name = "book_id")
    var bookId: Long = 0,
    var source: String = "",
    var status: String = "",
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
    @Column(name = "edited_at")
    var editedAt: Instant? = null,
    @Column(name = "created_at")
    var createdAt: Instant = Instant.EPOCH,
)

object CardSource {
    const val AUTO = "AUTO"
    const val CURATED = "CURATED"
}

object CardStatus {
    const val PUBLISHED = "PUBLISHED"
}
