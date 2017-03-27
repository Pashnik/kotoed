@file:Suppress("NOTHING_TO_INLINE")

package org.jetbrains.research.kotoed.util

import com.google.common.primitives.Chars
import com.hazelcast.util.Base64
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.eventbus.Message
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpResponse
import kotlinx.coroutines.experimental.CoroutineExceptionHandler
import kotlinx.coroutines.experimental.Unconfined
import java.io.BufferedReader
import java.io.InputStream
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.buildSequence
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

inline suspend fun vxu(crossinline cb: (Handler<AsyncResult<Void?>>) -> Unit): Void? =
        suspendCoroutine { cont ->
            cb(Handler { res ->
                if (res.succeeded()) cont.resume(res.result())
                else cont.resumeWithException(res.cause())
            })
        }

inline suspend fun <T> vxt(crossinline cb: (Handler<T>) -> Unit): T =
        suspendCoroutine { cont ->
            cb(Handler { res -> cont.resume(res) })
        }

inline suspend fun <T> vxa(crossinline cb: (Handler<AsyncResult<T>>) -> Unit): T =
        suspendCoroutine { cont ->
            cb(Handler { res ->
                if (res.succeeded()) cont.resume(res.result())
                else cont.resumeWithException(res.cause())
            })
        }

inline suspend fun <reified T> Loggable.vxal(crossinline cb: (Handler<AsyncResult<T>>) -> Unit): T {
    val res = vxa(cb)
    if (T::class.isSubclassOf(HttpResponse::class)) {
        log.info((res as HttpResponse<*>).bodyAsString())
    } else {
        log.info(res)
    }
    return res
}

object UnconfinedWithExceptions

inline fun UnconfinedWithExceptions(crossinline handler: (Throwable) -> Unit) =
        object : AbstractCoroutineContextElement(CoroutineExceptionHandler.Key), CoroutineExceptionHandler, DelegateLoggable {
            override val loggingClass = UnconfinedWithExceptions::class.java

            override fun handleException(context: CoroutineContext, exception: Throwable) {
                log.error(exception)
                handler(exception)
            }
        } + Unconfined

inline fun <T> UnconfinedWithExceptions(msg: Message<T>) =
        object : AbstractCoroutineContextElement(CoroutineExceptionHandler.Key), CoroutineExceptionHandler, DelegateLoggable {
            override val loggingClass = UnconfinedWithExceptions::class.java

            override fun handleException(context: CoroutineContext, exception: Throwable) {
                log.error(exception)
                msg.reply(
                        JsonObject(
                                "result" to "failed",
                                "error" to exception.message
                        )
                )
            }
        } + Unconfined

inline fun UnconfinedWithExceptions(ctx: RoutingContext) =
        object : AbstractCoroutineContextElement(CoroutineExceptionHandler.Key), CoroutineExceptionHandler, DelegateLoggable {
            override val loggingClass = UnconfinedWithExceptions::class.java

            override fun handleException(context: CoroutineContext, exception: Throwable) {
                log.error(exception)
                ctx.jsonResponse().end(
                        JsonObject(
                                "result" to "failed",
                                "error" to exception.message
                        )
                )
            }
        } + Unconfined

inline fun <T> defaultWrapperHandlerWithExceptions(crossinline handler: (Message<T>) -> Unit) = { msg: Message<T> ->
    try {
        handler(msg)
    } catch (ex: Exception) {
        ex.printStackTrace()
        msg.reply(
                JsonObject(
                        "result" to "failed",
                        "error" to ex.message
                )
        )
    }
}

inline fun <T> ((Message<T>) -> Unit).withExceptions() =
        defaultWrapperHandlerWithExceptions(this)

interface Loggable {
    val log: Logger
        get() = LoggerFactory.getLogger(javaClass)
}

interface DelegateLoggable: Loggable {
    val loggingClass: Class<*>

    override val log get() = LoggerFactory.getLogger(loggingClass)
}

inline fun base64Encode(v: CharSequence): String = String(Base64.encode(v.toString().toByteArray()))

fun Enum.Companion.valueOf(value: String, klass: KClass<*>) =
        klass.java.getMethod("valueOf", String::class.java).invoke(null, value)

inline fun <reified E : Enum<E>> Enum.Companion.valueOf(value: String) =
        valueOf(value, E::class) as E

fun String.unquote() =
        when {
            startsWith("\"") && endsWith("\"") -> drop(1).dropLast(1)
            startsWith("'") && endsWith("'") -> drop(1).dropLast(1)
            else -> this
        }

inline fun <T> T?.ignore(): Unit = Unit

fun BufferedReader.allLines() = buildSequence {
    var line = this@allLines.readLine()
    while(line != null) {
        yield(line)
        line = this@allLines.readLine()
    }
    this@allLines.close()
}

fun Sequence<String>.linesAsByteSequence(): Sequence<Byte> =
        flatMap { "$it\n".asSequence() }
                .flatMap { Chars.toByteArray(it).asSequence() }

fun Sequence<Byte>.asInputStream(): InputStream =
        object: InputStream() {
            val it = iterator()
            override fun read(): Int {
                if(!it.hasNext()) return -1
                return it.next().toInt()
            }

            override fun close() = forEach {}
        }


