package org.magicghostvu.actor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.magicghostvu.actor.timer.DelayedMessage
import org.magicghostvu.actor.timer.SingleTimerData
import org.magicghostvu.actor.timer.TimerManData
import org.magicghostvu.mlogger.ActorLogger

object Behaviors {

    // dummy object
    private val o1 = O1
    public fun <T> same(): Behavior<T> {
        return o1 as Behavior<T>
    }

    private val o2 = O2
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
    // và actor crash sẽ không gây stop scope ban đầu
    @OptIn(ObsoleteCoroutinesApi::class)
    private fun <T> CoroutineScope.spawn(
        capacity: Int,
        debug: Boolean = false,
        createNewScope: Boolean = false,
        timerExact: Boolean,
        name: String,
        factory: suspend () -> Behavior<T>
    ): MActorRef<T> {
        val scopeSpawnActor =
            if (createNewScope) {
                val newContext = coroutineContext + SupervisorJob()
                CoroutineScope(newContext)
            } else {
                this
            }


        val futureForInternalScope = CompletableDeferred<CoroutineScope>()

        val internalChannel = scopeSpawnActor.actor<Any>(capacity = capacity) {
            val logger = ActorLogger.logger

            futureForInternalScope.complete(this)

            val timerMan = TimerManData<T>(this, debug, timerExact)
            var state = factory()


            suspend fun unwrapBehavior(behavior: Behavior<T>): Behavior<T> {
                var tmp: Behavior<T> = behavior
                while (tmp is TimerBehavior<T> || tmp is SetUpBehavior<T>) {
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


                /*if (debug) {
                    logger.debug("msg internal come {}", messageToProcess)
                }*/


                // impossible
                // but a bug here, so we will fix this
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

        return if (createNewScope) {
            TopLevelActorRef<T>(internalChannel as SendChannel<T>, name)
        } else {
            val r = ChildActorRef<T>(internalChannel as SendChannel<T>, name, this)
            scopeSpawnActor.launch {
                // set value for scope inside??
                r.ownScope = futureForInternalScope.await()
                ActorLogger.logger.info("own scope for children set to {}", r.ownScope)
            }
            r
        }

    }

    // actor mới này nếu stop an toàn thì không affect đến parent
    // nếu crash sẽ gây crash parents
    // parent stop(dù có an toàn hay không) sẽ stop tất cả các con
    @OptIn(ObsoleteCoroutinesApi::class)
    fun <T> ActorScope<*>.spawnChild(
        name: String,
        debug: Boolean = false,
        capacity: Int = Channel.UNLIMITED,
        timerExact: Boolean = false,
        factory: suspend () -> Behavior<T>
    ): MActorRef<T> {
        return spawn(capacity, debug, createNewScope = false, timerExact, name, factory)
    }

    fun <T> CoroutineScope.spawnNew(
        name: String,
        debug: Boolean = false,
        capacity: Int = Channel.UNLIMITED,
        timerExact: Boolean = false,
        factory: suspend () -> Behavior<T>
    ): MActorRef<T> {
        return spawn(capacity, debug, createNewScope = true, timerExact, name, factory)
    }


}