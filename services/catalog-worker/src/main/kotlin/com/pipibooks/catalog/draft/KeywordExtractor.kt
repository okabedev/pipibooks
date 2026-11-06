package com.pipibooks.catalog.draft

/**
 * 키워드 후보를 목차 항목의 머리말, 소개 명사 빈도, 제목 단어 순으로 모아 중복 없이 최대 [MAX]개를 돌려준다.
 * 2자 이상 한글 단어만 쓰고, 끝의 조사를 뗀 뒤(떼고도 2자 이상 남을 때만, '이'는 명사 끝일 수 있어 떼지 않는다)
 * 2자 이상이고 불용어(조사를 뗀 형태 기준)가 아닌 것만 남긴다. 후보가 3개 미만이면 있는 만큼만 돌려주고(빈 목록 가능),
 * 호출하는 쪽이 슬라이드 생략 여부를 정한다.
 */
object KeywordExtractor {
    const val MAX = 6
    private val word = Regex("[가-힣]{2,}")
    private val tocSeparator = Regex("[\\n\\r;]+|\\s--\\s|\\s\\d{1,2}\\.\\s")
    private const val PARTICLES = "은는가을를의에도로와과들"
    private const val MIN_STEM = 3
    private val longParticles = listOf("에서", "에게", "으로", "마다", "까지", "부터", "처럼", "보다")
    private val stopwords =
        setOf(
            "그리고",
            "그러나",
            "하지만",
            "그래서",
            "또한",
            "이책",
            "우리",
            "모든",
            "통해",
            "위해",
            "대한",
            "있는",
            "있다",
            "없는",
            "하는",
            "한다",
            "된다",
            "이다",
            "때문",
            "그것",
            "이것",
            "저자",
            "지음",
            "이야기",
            "것이다",
            "같은",
            "다른",
            "함께",
            "가장",
            "매우",
            "이후",
            "에서",
            "에게",
            "까지",
            "부터",
            "합니다",
            "입니다",
            "있습니다",
            "수있는",
            "무엇",
            "어떻게",
            "그런",
            "이런",
            "여러",
            "많은",
            "다시",
            "처음",
            "마지막",
            "머리말",
            "프롤로그",
            "에필로그",
            "차례",
            "목차",
            "감사의",
            "옮긴이",
            "만난",
            "작은",
            "큰",
            "새로운",
            "담았다",
            "담은",
            "읽는",
            "읽고",
        )

    fun extract(
        title: String,
        introduction: String?,
        tableOfContents: String?,
    ): List<String> =
        (tocHeads(tableOfContents) + introNouns(introduction) + words(title).map(::stem))
            .filter { it.length >= 2 && it !in stopwords }
            .distinct()
            .take(MAX)

    private fun words(text: String) = word.findAll(text).map { it.value }.toList()

    /** 목차 항목마다 첫 한글 단어. */
    private fun tocHeads(toc: String?): List<String> =
        toc
            ?.split(tocSeparator)
            .orEmpty()
            .mapNotNull { item -> word.find(item)?.value?.let(::stem) }

    /** 빈도 내림차순, 같으면 먼저 나온 순. */
    private fun introNouns(intro: String?): List<String> {
        val counts = linkedMapOf<String, Int>()
        words(intro.orEmpty()).map(::stem).filter { it.length >= 2 && it !in stopwords }.forEach {
            counts[it] = (counts[it] ?: 0) + 1
        }
        // linkedMap 순서가 먼저 나온 순이고 sortedByDescending은 안정 정렬이다.
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** 끝의 조사를 떼는 단순 규칙. 떼고 나서 두 글자 이상 남을 때만 적용한다('이'는 떼지 않는다). */
    private fun stem(w: String): String {
        val suffix = longParticles.firstOrNull { w.length > it.length + 1 && w.endsWith(it) }
        return when {
            suffix != null -> w.dropLast(suffix.length)
            w.length >= MIN_STEM && w.last() in PARTICLES -> w.dropLast(1)
            else -> w
        }
    }
}
