package org.magicghostvu.run

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.magicghostvu.actor.AbstractBehaviour
import org.magicghostvu.actor.Behavior
import org.magicghostvu.actor.Behaviors

import org.magicghostvu.actor.Behaviors.spawn
import org.magicghostvu.mlogger.ActorLogger


sealed class Msg
class Msg1 : Msg()
class Msg2(val from:String) : Msg()
class Msg3 : Msg()

class State1(var i: Int) : AbstractBehaviour<Msg>() {
    private val logger = ActorLogger.logger
    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        logger.info("msg {} come", message)
        if (message is Msg1) {
            i++
            return Behaviors.withTimer {
                it.startFixedRateTimer("key1", Msg2("timer"), 1000, 2000)
                State2(0.0)
            }
        } else {
            return Behaviors.same()
        }
    }
}

class State2(var d: Double) : AbstractBehaviour<Msg>() {
    private val logger = ActorLogger.logger
    override suspend fun onReceive(message: Msg): Behavior<Msg> {
        return when (message) {
            is Msg1 -> {
                Behaviors.withTimer {
                    it.cancel("key1")
                    logger.info("call cancel key 1")
                    Behaviors.same()
                }
            }
            is Msg2 -> {
                logger.info("msg 2 come from {}", message.from)
                d++
                logger.info("d is {}", d)
                Behaviors.same()
            }
            is Msg3 -> {
                logger.info("msg 3 come")
                Behaviors.stopped()
            }
        }
    }
}


fun main(arr: Array<String>) {
    runBlocking {
        val logger = ActorLogger.logger
        val actorRef = spawn(true) {
            State1(0)
        }

        actorRef.tell(Msg2("normal"))
        actorRef.tell(Msg1())

        delay(5000)
        logger.info("done delay")
        actorRef.tell(Msg1())
        actorRef.tell(Msg2("normal"))
    }
}