package org.magicghostvu.actor.timer

import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.magicghostvu.mlogger.ActorLogger
import MTimerUtils.timerExact
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


// not thread safe
//các hàm phải được gọi bên trong actor
@OptIn(ObsoleteCoroutinesApi::class)
class TimerManData<T> internal constructor(
    private val scope: ActorScope<Any>,
    private val debug: Boolean,
    private val timerExact: Boolean
) {
    private val idToTimerData = mutableMapOf<Any, TimerData>()
    private val idToGeneration = mutableMapOf<Any, Int>()

    private val logger = ActorLogger.logger

    // todo: thêm các hàm start single timer/ schedule ...
    //  cancel with key, cancel all...

    internal fun keyExist(key: Any): Boolean {
        return idToGeneration.containsKey(key)
    }

    internal fun getCurrentGeneration(key: Any): Int {
        return idToGeneration.getValue(key)
    }

    internal fun removeKey(key: Any, cancelJob: Boolean) {
        /*if (idToTimerData.containsKey(key)) {
            val timerData = idToTimerData.getValue(key)
            if (cancelJob) {
                timerData.job.cancel()
            }
        }*/


        val timerData = idToTimerData[key]
        if (timerData != null && cancelJob) {
            timerData.job.cancel()
        }

        idToTimerData.remove(key)
        idToGeneration.remove(key)
    }

    public fun getTimerData(key: Any): TimerData {
        return idToTimerData.getValue(key)
    }

    fun startSingleTimer(key: Any, message: T, delayMillis: Long): TimerData {
        removeKey(key, true)
        // phải lấy generation trước launch
        // nếu trong launch sẽ bị data race

        val expectGeneration = idToGeneration.compute(key) { _, oldValue ->
            if (oldValue == null) {
                0
            } else {
                oldValue + 1
            }
        } ?: throw IllegalArgumentException("can not be here")
        val messageToSend = DelayedMessage(
            message,
            key,
            expectGeneration
        )

        val job = scope.launch {
            delay(delayMillis)
            //logger.info("exe timer")
            scope.channel.send(
                messageToSend
            )
        }
        //idToJob[key] = job
        val res = SingleTimerData(key, job, this)
        idToTimerData[key] = res
        return res
    }

    @OptIn(ExperimentalTime::class)
    fun startFixedRateTimer(key: Any, message: T, initDelayMilli: Long, period: Long): TimerData {
        removeKey(key, true)
        // phải lấy generation trước launch
        // nếu trong launch sẽ bị data race

        val expectGeneration = idToGeneration.compute(key) { _, oldValue ->
            return@compute if (oldValue == null) {
                0
            } else {
                oldValue + 1
            }
        } ?: throw IllegalArgumentException("can not be here")
        val messageToSend = DelayedMessage(
            message,
            key,
            expectGeneration
        )

        val job: Job = if (timerExact) {
            scope.timerExact(
                interval = period.milliseconds,
                startDelay = initDelayMilli.milliseconds,
            ) {
                scope.channel.send(messageToSend)
            }
        } else {
            scope.launch {
                if (initDelayMilli > 0) {
                    delay(initDelayMilli)
                }
                while (true) {
                    scope.channel.send(messageToSend)
                    delay(period)
                }
            }
        }


        val res = PeriodicTimerData(key, job, this)
        idToTimerData[key] = res
        return res
    }


    fun startSingleTimer(message: T, delayMillis: Long): TimerData {
        return startSingleTimer(message as Any, message, delayMillis)
    }


    fun startFixedRateTimer(message: T, initDelay: Long, period: Long): TimerData {
        return startFixedRateTimer(message as Any, message, initDelay, period)
    }


    fun cancelAll() {
        idToTimerData.values.forEach {
            it.job.cancel()
        }
        idToTimerData.clear()
        idToGeneration.clear()
        logger.info("cancel all called")
    }

    fun cancel(key: Any) {
        removeKey(key, true)
        if (debug) {
            logger.info("timer with key {} canceled", key)
        }
    }

}

internal data class DelayedMessage<T>(val message: T, val keyTimer: Any, val generationAtCreate: Int)