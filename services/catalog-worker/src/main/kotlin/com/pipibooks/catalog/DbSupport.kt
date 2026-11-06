package com.pipibooks.catalog

import org.springframework.r2dbc.core.DatabaseClient

/** R2DBC는 null을 `bind`할 수 없어 타입을 함께 넘기는 `bindNull`을 써야 한다. */
inline fun <reified T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
): DatabaseClient.GenericExecuteSpec = if (value == null) bindNull(name, T::class.javaObjectType) else bind(name, value)
