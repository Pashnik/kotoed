package org.jetbrains.research.kotoed.util

import io.vertx.ext.web.client.HttpRequest

fun <T> HttpRequest<T>.putHeader(name: CharSequence, value: CharSequence): HttpRequest<T> =
        this.putHeader(name.toString(), value.toString())