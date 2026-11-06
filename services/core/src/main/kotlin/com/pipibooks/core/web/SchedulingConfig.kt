package com.pipibooks.core.web

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** `@Scheduled` 작업(카드 동기화 등)을 켠다. */
@Configuration
@EnableScheduling
class SchedulingConfig
