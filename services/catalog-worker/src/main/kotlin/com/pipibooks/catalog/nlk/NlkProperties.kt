package com.pipibooks.catalog.nlk

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

/** `nlk.*` 설정. 인증키가 비어 있어도 기동하며, 호출 시 [NlkAuthException]이 난다. */
@ConfigurationProperties("nlk")
data class NlkProperties(
    @DefaultValue("https://www.nl.go.kr") val baseUrl: String = "https://www.nl.go.kr",
    @DefaultValue("") val certKey: String = "",
    @DefaultValue("1.0") val permitsPerSecond: Double = 1.0,
    @DefaultValue("500") val pageSize: Int = 500,
    @DefaultValue("200") val retryBaseDelayMs: Long = 200,
    @DefaultValue("10") val timeoutSeconds: Long = 10,
)
