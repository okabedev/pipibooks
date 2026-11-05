package com.pipibooks.common.slide

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.LocalDate

/** 카드 슬라이드 한 장의 내용. JSON에서는 `type` 필드(COVER, HOOK, INFO, KEYWORDS, FOR_WHOM, QUOTE)로 구분한다. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(SlidePayload.Cover::class),
    JsonSubTypes.Type(SlidePayload.Hook::class),
    JsonSubTypes.Type(SlidePayload.Info::class),
    JsonSubTypes.Type(SlidePayload.Keywords::class),
    JsonSubTypes.Type(SlidePayload.ForWhom::class),
    JsonSubTypes.Type(SlidePayload.Quote::class),
)
sealed interface SlidePayload {
    @JsonTypeName("COVER")
    data class Cover(
        val title: String,
        val author: String,
        val coverUrl: String?,
    ) : SlidePayload

    @JsonTypeName("HOOK")
    data class Hook(
        val sentences: List<String>,
    ) : SlidePayload

    @JsonTypeName("INFO")
    data class Info(
        val author: String,
        val publisher: String,
        val publishedOn: LocalDate?,
        val pages: Int?,
        val kdcLabel: String?,
        val source: String = "국립중앙도서관",
    ) : SlidePayload

    @JsonTypeName("KEYWORDS")
    data class Keywords(
        val keywords: List<String>,
    ) : SlidePayload

    @JsonTypeName("FOR_WHOM")
    data class ForWhom(
        val lines: List<String>,
    ) : SlidePayload

    @JsonTypeName("QUOTE")
    data class Quote(
        val text: String,
        val page: Int?,
    ) : SlidePayload
}
