package org.magicghostvu.run

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ActorScope
import org.magicghostvu.actor.AbstractBehaviour
import org.magicghostvu.actor.Behavior
import org.magicghostvu.actor.Behaviors
import org.magicghostvu.actor.Behaviors.spawnChild
import org.magicghostvu.actor.Behaviors.spawnNew
import org.magicghostvu.actor.MActorRef
import org.magicghostvu.mlogger.ActorLogger


sealed class Msg
class Msg1 : Msg()
class Msg2(val from: String) : Msg()
class Msg3(val repTo: CompletableDeferred<MActorRef<Msg>>) : Msg()
class Msg4() : Msg()

@OptIn(ObsoleteCoroutinesApi::class)
class State1(scope: ActorScope<Msg>, var i: Int) : AbstractBehaviour<Msg>(scope) {
    private val logger = ActorLogger.logger

    private lateinit var child: MActorRef<Msg>

    private var setChild = false

    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        //logger.info("msg {} come", message)
        if (message is Msg1) {
            i++
            if (!setChild) {
                child = scope.spawnChild {
                    State2.setup()
                }
                logger.info("set child success")
                setChild = true
            }
            logger.info("parent received msg 1")
            return Behaviors.same()
        } else if (message is Msg3) {
            val result = message.repTo
            result.complete(child)
            return Behaviors.same()
        } else if (message is Msg2) {
            logger.info("msg 2 come to parent")
            //logger.info("stop parent")
            //throw IllegalArgumentException("crash parent")
            return Behaviors.same()
        } else {
            logger.info("stop parent")
            return Behaviors.stopped()
        }
    }
}

@OptIn(ObsoleteCoroutinesApi::class)
class State2(scope: ActorScope<Msg>, var d: Double) : AbstractBehaviour<Msg>(scope) {
    private val logger = ActorLogger.logger
    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        return when (message) {
            is Msg1 -> {
                logger.info("child received msg1")
                Behaviors.same()
            }
            is Msg2 -> {
                logger.info("child received msg 2 come from {}", message.from)
                d++
                logger.info("d is {}", d)

                Behaviors.same()
                //throw IllegalArgumentException("crash child")
                //Behaviors.stopped()
            }
            is Msg3 -> {
                logger.info("msg 3 come")
                throw IllegalArgumentException("crash child")
            }
            is Msg4 -> {
                logger.info("stop child")
                Behaviors.stopped()
            }
        }
    }

    companion object {
        fun setup(): Behavior<Msg> {
            return Behaviors.withTimer { timer ->
                timer.startFixedRateTimer(Msg1(), 0, 1000)

                timer.startSingleTimer(Msg4(), 5000)

                Behaviors.setUp { State2(it, 0.0) }
            }
        }
    }
}


@OptIn(ObsoleteCoroutinesApi::class)
fun main(arr: Array<String>) {
    runBlocking {
        val logger = ActorLogger.logger


        launch {
            while (true) {
                logger.info("alive, scope is {}", this)
                delay(3000)
            }
        }

        val parent = spawnNew<Msg> {
            Behaviors.withTimer { timer ->
                timer.startFixedRateTimer(
                    Msg2("ádasd"),
                    0,
                    1000
                )
                Behaviors.setUp {
                    State1(it, 0)
                }
            }
        }
        parent.tell(Msg1())

        /*delay(5000)
        parent.tell(Msg4())*/

    }
}