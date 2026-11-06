package com.pipibooks.catalog.nlk

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 국립중앙도서관 ISBN 서지정보 API(`/seoji/SearchApi.do`)의 응답 모델과 파싱.
 *
 * 응답 봉투는 공개 문서 기준 가정이다(실측 전). 성공: `{TOTAL_COUNT, PAGE_NO, docs:[{...}]}`,
 * 오류: `{RESULT:"ERROR", ERR_CODE:"010", ERR_MESSAGE:"..."}`. 실제 키를 받아 측정하면 이 파일만 고친다.
 */
data class NlkRecord(
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val eaIsbn: String? = null,
    val setIsbn: String? = null,
    val publishPredate: String? = null,
    val kdc: String? = null,
    val page: String? = null,
    val titleUrl: String? = null,
    val bookIntroduction: String? = null,
    val bookTbCnt: String? = null,
    val eaAddCode: String? = null,
    val form: String? = null,
    val ebookYn: String? = null,
)

data class NlkPage(
    val totalCount: Int,
    val records: List<NlkRecord>,
)

open class NlkException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 인증키 오류(`010`·`011`) 또는 키 미설정. 재시도하지 않는다. */
class NlkAuthException(
    message: String,
) : NlkException(message)

/** 재시도 후에도 실패한 장애. */
class NlkUnavailableException(
    message: String,
    cause: Throwable? = null,
) : NlkException(message, cause)

/** 응답 본문이 일시적 오류(코드 `000`)라 재시도할 수 있음을 알리는 내부 신호. */
internal class NlkRetryableException(
    message: String,
) : NlkException(message)

internal object NlkParser {
    private val mapper = JsonMapper.builder().build()
    private val authCodes = setOf("010", "011")

    fun parse(body: String): NlkPage {
        val root = mapper.readTree(body)
        val errCode = root.text("ERR_CODE")
        if (errCode != null) throw errorFor(errCode, root.text("ERR_MESSAGE"))
        val records =
            root
                .get("docs")
                ?.values()
                ?.map { toRecord(it) }
                .orEmpty()
        val total = root.text("TOTAL_COUNT")?.toIntOrNull() ?: records.size
        return NlkPage(total, records)
    }

    private fun errorFor(
        code: String,
        message: String?,
    ): NlkException {
        val text = "국립중앙도서관 오류 $code: ${message.orEmpty()}"
        return when (code) {
            in authCodes -> NlkAuthException(text)
            "000" -> NlkRetryableException(text)
            else -> NlkUnavailableException(text)
        }
    }

    private fun toRecord(n: JsonNode) =
        NlkRecord(
            title = n.text("TITLE"),
            author = n.text("AUTHOR"),
            publisher = n.text("PUBLISHER"),
            eaIsbn = n.text("EA_ISBN"),
            setIsbn = n.text("SET_ISBN"),
            publishPredate = n.text("PUBLISH_PREDATE"),
            kdc = n.text("KDC"),
            page = n.text("PAGE"),
            titleUrl = n.text("TITLE_URL"),
            bookIntroduction = n.text("BOOK_INTRODUCTION"),
            bookTbCnt = n.text("BOOK_TB_CNT"),
            eaAddCode = n.text("EA_ADD_CODE"),
            form = n.text("FORM"),
            ebookYn = n.text("EBOOK_YN"),
        )

    private fun JsonNode.text(field: String): String? = get(field)?.takeIf { !it.isNull }?.asString()
}
