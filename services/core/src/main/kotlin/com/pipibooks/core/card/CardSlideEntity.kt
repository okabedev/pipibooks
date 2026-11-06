package com.pipibooks.core.card

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "card_slide", schema = "core")
class CardSlideEntity(
    @Id
    var id: Long = 0,
    @Column(name = "card_id")
    var cardId: Long = 0,
    var position: Int = 0,
    var type: String = "",
    /** 슬라이드 JSON 문자열(`type` 구분자 포함). 변환은 서비스가 Jackson으로 직접 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    var payload: String = "{}",
)
