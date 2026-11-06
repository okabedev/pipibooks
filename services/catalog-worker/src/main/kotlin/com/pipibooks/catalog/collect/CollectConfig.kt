package com.pipibooks.catalog.collect

import com.pipibooks.catalog.nlk.NlkProperties
import com.pipibooks.catalog.nlk.NlkRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

@ConfigurationProperties("collect")
data class CollectProperties(
    @DefaultValue("14") val windowDays: Int = 14,
)

/** 요청보다 오래 사는 백그라운드 수집용 코루틴 스코프. 종료 시 취소한다. */
class ApplicationScope :
    CoroutineScope,
    DisposableBean {
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default

    override fun destroy() = cancel()
}

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NlkProperties::class, CollectProperties::class)
class CollectConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    @Bean
    fun nlkRateLimiter(props: NlkProperties) = NlkRateLimiter(props.permitsPerSecond)

    @Bean
    fun applicationScope() = ApplicationScope()
}
