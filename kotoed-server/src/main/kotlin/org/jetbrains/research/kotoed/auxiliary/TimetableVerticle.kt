package org.jetbrains.research.kotoed.auxiliary

import org.jetbrains.research.kotoed.auxiliary.data.TimetableMessage
import org.jetbrains.research.kotoed.eventbus.Address
import org.jetbrains.research.kotoed.util.*
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@AutoDeployable
class TimetableVerticle: AbstractKotoedVerticle(), Loggable {

    private val que = PriorityQueue<TimetableMessage>(compareBy{ it.time })

    override suspend fun start() {
        vertx.setPeriodic(1000L * 60L){ handleTick() }
        super.start()
    }

    private fun handleTick() = spawn {
        val now = LocalDateTime.now(Clock.systemUTC())
        while(que.isNotEmpty()) {
            val current = que.peek()
            if(current.time <= now) {
                que.remove()
                doSend(current);
            } else break;
        }
    }

    private suspend fun doSend(current: TimetableMessage) {
        val eb = vertx.eventBus()
        when(current.replyTo) {
            null -> eb.sendJsonable(current.sendTo, current.message)
            else -> {
                val resp = eb.sendAsync(current.sendTo, current.message)
                eb.sendJsonable(current.replyTo, resp.body())
            }
        }
    }

    @JsonableEventBusConsumerFor(Address.Schedule)
    suspend fun handleTimetable(m: TimetableMessage) {
        val now = LocalDateTime.now(Clock.systemUTC())
        log.trace("Now = $now")
        log.trace("Epoch = ${now.tryToJson()}")
        if(now > m.time) doSend(m);
        else que.offer(m);
    }
}
