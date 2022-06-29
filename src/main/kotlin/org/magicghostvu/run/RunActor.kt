package org.magicghostvu.run

import kotlinx.coroutines.*
import org.magicghostvu.actor.AbstractBehaviour
import org.magicghostvu.actor.Behavior
import org.magicghostvu.actor.Behaviors

import org.magicghostvu.actor.Behaviors.spawn
import org.magicghostvu.actor.MActorRef
import org.magicghostvu.mlogger.ActorLogger
import kotlin.math.log


sealed class Msg
class Msg1 : Msg()
class Msg2(val from: String) : Msg()
class Msg3(val repTo: CompletableDeferred<MActorRef<Msg>>) : Msg()

class State1(scope: CoroutineScope, var i: Int) : AbstractBehaviour<Msg>(scope) {
    private val logger = ActorLogger.logger

    private lateinit var child: MActorRef<Msg>

    private var setChild = false

    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        //logger.info("msg {} come", message)
        if (message is Msg1) {
            i++
            if (!setChild) {
                child = scope.spawn() {
                    State2(scope, 0.0)
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
        } else {
            logger.info("stop parent")
            //throw IllegalArgumentException("crash parent")
            return Behaviors.stopped()
        }
    }
}

class State2(scope: CoroutineScope, var d: Double) : AbstractBehaviour<Msg>(scope) {
    private val logger = ActorLogger.logger
    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        return when (message) {
            is Msg1 -> {
                logger.info("child received msg1")
                Behaviors.same()
            }
            is Msg2 -> {
                logger.info("msg 2 come from {}", message.from)
                d++
                logger.info("d is {}", d)

                throw IllegalArgumentException("crash child")
                //Behaviors.stopped()
            }
            is Msg3 -> {
                logger.info("msg 3 come")
                //Behaviors.stopped<Msg>()
                throw IllegalArgumentException("crash child")
            }
        }
    }
}


fun main(arr: Array<String>) {
    runBlocking {
        val logger = ActorLogger.logger
        /*val actorRef = spawn {
            delay(1000)
            State1(0)
        }

        actorRef.tell(Msg2("normal"))
        actorRef.tell(Msg1())

        delay(5000)
        logger.info("done delay")
        actorRef.tell(Msg1())
        actorRef.tell(Msg2("normal"))*/


        launch {
            while (true) {
                logger.info("alive, scope is {}", this)
                delay(3000)
            }
        }

        // khi dùng supervisor thì nó nên được add vào sau context hiện tại

        val parent = spawn {
            Behaviors.withTimer<Msg> {
                it.startFixedRateTimer(Msg1(), 0, 1000)
                State1(this, 0)
            }
        }

        val result = CompletableDeferred<MActorRef<Msg>>()
        delay(500)
        parent.tell(Msg3(result))


        val child = result.await()
        logger.info("received child")
        launch(SupervisorJob()) {
            while (true) {
                child.tell(Msg1())
                delay(1000)
            }
        }

        launch {
            delay(5000)
            child.tell(Msg2("crash child"))
        }

        delay(5000)
        parent.tell(Msg2("crash parent"))




        delay(1000000)
    }
}