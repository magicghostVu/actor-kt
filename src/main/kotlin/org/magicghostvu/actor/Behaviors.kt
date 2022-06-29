package org.magicghostvu.actor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import org.magicghostvu.actor.timer.DelayedMessage
import org.magicghostvu.actor.timer.SingleTimerData
import org.magicghostvu.actor.timer.TimerManData
import org.magicghostvu.mlogger.ActorLogger

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

    // khi actor bị crash nó sẽ stop scope đã tạo ra actor
    // vậy nên xem xét mỗi khi tạo actor nên tạo scope mới???
    // nếu actor được dừng an toàn thì scope sẽ không có affect
    // nên xem xét quay lại cài đặt cũ khi stop actor thì sẽ stop luôn scope hiện tại
    // nếu createNewScope= true thì sẽ create một scope mới cho actor
    // actor crash sẽ không gây crash scope ban đầu
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


            suspend fun unwrapTimerBehavior(timerBehavior: TimerBehavior<T>): Behavior<T> {
                var tmp: Behavior<T> = timerBehavior
                while (tmp is TimerBehavior<T>) {
                    tmp = tmp.timerFunc(timerMan)
                }
                return tmp
            }
            if (state is TimerBehavior<T>) {
                state = unwrapTimerBehavior(state)
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
                        logger.debug("stopped come, cancel the channel")
                    }
                    timerMan.cancelAll()
                    this.channel.cancel()
                    return@consumeEach
                }

                if (tmp is TimerBehavior<T>) {
                    val tmp2 = tmp as TimerBehavior<T>
                    tmp = unwrapTimerBehavior(tmp2)
                }

                // recheck with same and stopped here???
                // sau chỗ này state bắt buộc phải là AbstractBehavior
                if (tmp == stopped<T>()) {
                    if (debug) {
                        logger.debug("cancel channel after unwrap timer behavior")
                    }
                    timerMan.cancelAll()
                    this.channel.cancel()
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