package org.magicghostvu.actor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import org.magicghostvu.actor.timer.DelayedMessage
import org.magicghostvu.actor.timer.SingleTimerData
import org.magicghostvu.actor.timer.TimerManData
import org.magicghostvu.mlogger.ActorLogger
import java.lang.management.ManagementFactory

object Behaviors {

    // dummy object
    private val o1 = Behavior<Any>()
    public fun <T> same(): Behavior<T> {
        return o1 as Behavior<T>
    }

    private val o2 = Behavior<Any>()
    public fun <T> stopped(): Behavior<T> {
        return o2 as Behavior<T>
    }


    public fun <T> withTimer(doWithTimer: suspend (TimerManData<T>) -> Behavior<T>): Behavior<T> {
        return TimerBehavior(doWithTimer)
    }


    @OptIn(ObsoleteCoroutinesApi::class)
    public fun <T> setUp(factory: suspend (ActorScope<T>) -> Behavior<T>): Behavior<T> {
        return SetUpBehavior(factory)
    }


    // theo mặc định khi actor bị crash nó sẽ stop(cancel) scope đã tạo ra actor
    // khi actor stop an toàn thì sẽ không affect đến scope ban đầu
    // nếu createNewScope = true thì sẽ create một scope mới cho actor kèm với supervisor
    // actor crash sẽ không gây stop scope ban đầu
    @OptIn(ObsoleteCoroutinesApi::class)
    fun <T> CoroutineScope.spawn(
        debug: Boolean = false,
        createNewScope: Boolean = false,
        factory: suspend () -> Behavior<T>
    ): MActorRef<T> {
        val scopeSpawnActor =
            if (createNewScope) {
                val newContext = coroutineContext + SupervisorJob()
                CoroutineScope(newContext)
            } else {
                this
            }
        val internalChannel = scopeSpawnActor.actor<Any>(capacity = 10000) {


            val logger = ActorLogger.logger

            val timerMan = TimerManData<T>(this, debug)
            var state = factory()


            suspend fun unwrapBehavior(behavior: Behavior<T>): Behavior<T> {
                var tmp: Behavior<T> = behavior
                while (tmp !is AbstractBehaviour<T>) {
                    if (tmp is TimerBehavior<T>) {
                        tmp = tmp.timerFunc(timerMan)
                    } else if (tmp is SetUpBehavior<T>) {
                        tmp = tmp.factory(this as ActorScope<T>)
                    }
                }
                return tmp
            }
            if (state !is AbstractBehaviour<T>) {
                state = unwrapBehavior(state)
            }

            if (state !is AbstractBehaviour<T>) {
                throw IllegalArgumentException("init state must be abstract behavior")
            }

            channel.consumeEach {

                var messageToProcess: T? = null
                if (it is DelayedMessage<*>) {
                    // un-check cast
                    val dMsg = it as DelayedMessage<T>
                    val (msg, key, genFromMessage) = dMsg
                    if (!timerMan.keyExist(key)) {
                        // timer này đã bị huỷ, không xử lý message này
                        if (debug) {
                            logger.debug("msg {} come but key not exist, ignore", msg)
                        }

                        return@consumeEach
                    }
                    val currentGenThisKey = timerMan.getCurrentGeneration(key)
                    // timer này bị huỷ khi message đã được gửi
                    // không xử lý
                    if (currentGenThisKey != genFromMessage) {
                        if (debug) {
                            logger.debug("msg {} come but generation outdated, ignore", msg)
                        }
                        return@consumeEach
                    }

                    // ở đây chắc chắc state là Abstract behavior


                    // check nếu là single timer thì xoá
                    // vì nó đã hoàn thành nhiệm vụ
                    val timerData = timerMan.getTimerData(key)
                    if (timerData is SingleTimerData) {
                        timerMan.removeKey(key, false)
                    }
                    messageToProcess = msg
                } else {
                    // message bình thường
                    messageToProcess = it as T
                }


                if (debug) {
                    logger.debug("msg internal come {}", messageToProcess)
                }


                // impossible
                // but a bug here so we will fix this
                //if (state == same<T>()) return@consumeEach


                var tmp: Behavior<T> = (state as AbstractBehaviour<T>).onReceive(messageToProcess)
                if (tmp == same<T>()) return@consumeEach


                // kill actor
                // và kill tất cả timer, etc...
                // we are safe here
                if (tmp == stopped<T>()) {
                    if (debug) {
                        logger.debug("stopped come, cancel the channel, scope is {}", this)
                    }
                    this.cancel()
                    return@consumeEach
                }

                if (tmp !is AbstractBehaviour<T>) {
                    tmp = unwrapBehavior(tmp)
                }

                // recheck with same and stopped here???
                // sau chỗ này state bắt buộc phải là AbstractBehavior
                if (tmp == stopped<T>()) {
                    if (debug) {
                        logger.debug("cancel channel after unwrap timer behavior")
                    }
                    //timerMan.cancelAll()
                    this.cancel()
                    return@consumeEach
                }
                if (tmp is AbstractBehaviour<T>) {
                    state = tmp
                } else if (tmp == same<T>()) {
                    return@consumeEach
                } else {
                    logger.error("logic had some problem, review code")
                }
            }
        }

        return MActorRef(internalChannel as SendChannel<T>)
    }

}