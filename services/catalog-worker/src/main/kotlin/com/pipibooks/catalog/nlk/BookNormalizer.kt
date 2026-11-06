package com.pipibooks.catalog.nlk

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class NormalizedBook(
    val isbn13: String,
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val publishPredate: LocalDate? = null,
    val kdc: String? = null,
    val pages: Int? = null,
    val coverUrl: String? = null,
    val introduction: String? = null,
    val tableOfContents: String? = null,
    val eaAddCode: String? = null,
    val form: String? = null,
    val ebookYn: Boolean? = null,
    val setIsbn: String? = null,
)

/** 국립중앙도서관 응답을 저장 형태로 정리한다. 유효한 ISBN13과 제목이 없으면 버린다(`null`). */
object BookNormalizer {
    private const val ISBN_LENGTH = 13
    private const val EA_ADD_CODE_MAX = 5
    private const val DATE_LENGTH = 8
    private const val KDC_MAX = 16
    private const val FORM_MAX = 64
    private const val ISBN_MOD = 10
    private const val ISBN_ODD_WEIGHT = 3
    private const val ROLES = "지음|지은이|옮김|옮긴이|엮음|엮은이|그림|글|감수|편저|편|저|역|공저|공역|사진"
    private val trailingRole = Regex("(\\s+|\\s*[(\\[])($ROLES)[)\\]]?$")
    private val dateFormat = DateTimeFormatter.BASIC_ISO_DATE

    fun normalize(r: NlkRecord): NormalizedBook? {
        val isbn = isbn13(r.eaIsbn)
        val title = clean(r.title)
        return if (isbn == null || title == null) {
            null
        } else {
            NormalizedBook(
                isbn13 = isbn,
                title = title,
                author = author(r.author),
                publisher = clean(r.publisher),
                publishPredate = date(r.publishPredate),
                kdc = (clean(r.kdc) ?: major(r.subject))?.take(KDC_MAX),
                pages = clean(r.page)?.takeWhile(::isAsciiDigit)?.toIntOrNull(),
                coverUrl = clean(r.titleUrl)?.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://"),
                introduction = clean(r.bookIntroduction),
                tableOfContents = clean(r.bookTbCnt),
                eaAddCode = clean(r.eaAddCode)?.takeIf { it.length <= EA_ADD_CODE_MAX },
                form = clean(r.form)?.take(FORM_MAX),
                ebookYn = ebook(r.ebookYn),
                setIsbn = clean(r.setIsbn)?.filter(::isAsciiDigit)?.takeIf { it.length == ISBN_LENGTH },
            )
        }
    }

    /** 숫자만 남겨 13자리이고 검증숫자가 맞을 때만 돌려준다. */
    fun isbn13(raw: String?): String? {
        val digits = raw?.filter(::isAsciiDigit) ?: return null
        return digits.takeIf { it.length == ISBN_LENGTH && checkDigitOk(it) }
    }

    private fun checkDigitOk(d: String): Boolean {
        val sum = d.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else ISBN_ODD_WEIGHT }.sum()
        return sum % ISBN_MOD == 0
    }

    private fun isAsciiDigit(c: Char) = c in '0'..'9'

    private fun ebook(s: String?): Boolean? =
        when (clean(s)?.uppercase()) {
            "Y" -> true
            "N" -> false
            else -> null
        }

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    private fun date(s: String?): LocalDate? =
        try {
            clean(s)?.takeIf { it.length == DATE_LENGTH }?.let { LocalDate.parse(it, dateFormat) }
        } catch (_: DateTimeParseException) {
            null
        }

    /** `KDC`가 비는 실제 응답에서 `SUBJECT`의 한 자리 숫자(KDC 대분류)를 대신 쓴다. */
    private fun major(subject: String?): String? = clean(subject)?.takeIf { it.length == 1 && isAsciiDigit(it[0]) }

    /**
     * `역할 : 이름;역할 : 이름;` 형식이면 역할을 떼고 이름만 원래 순서로 잇는다(콜론 앞뒤 공백은 없을 수도 있다).
     * 콜론이 없는 형식(`홍길동 지음`, `A, B`)은 뒤쪽 역할어만 정리한다. 같은 이름은 한 번만 쓴다.
     */
    private fun author(s: String?): String? {
        val text = clean(s) ?: return null
        val names =
            if (text.contains(':')) {
                text.split(';').mapNotNull { segment ->
                    if (segment.contains(':')) clean(segment.substringAfter(':')) else stripRoles(segment.trim())
                }
            } else {
                text.split(';', ',').map { stripRoles(it.trim()) }
            }
        return names
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    }

    private fun stripRoles(name: String): String {
        var current = name
        while (true) {
            val next = current.replace(trailingRole, "").trim()
            if (next == current) return current
            current = next
        }
    }
}
