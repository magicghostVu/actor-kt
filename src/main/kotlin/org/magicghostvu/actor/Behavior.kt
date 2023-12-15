package org.magicghostvu.actor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.SendChannel
import org.magicghostvu.actor.timer.TimerManData
import org.magicghostvu.mlogger.ActorLogger


sealed class MActorRef<in Message>(
    private val internalChannel: SendChannel<Message>,
    val name: String
) {
    public suspend fun tell(message: Message) {
        try {
            internalChannel.send(message)
        } catch (e: Exception) {
            ActorLogger.logger.error("err while send message $message to actor $name", e)
        }
    }
}

internal class TopLevelActorRef<in Message>(
    internalChannel: SendChannel<Message>,
    name: String
) : MActorRef<Message>(internalChannel, name)


internal class ChildActorRef<in Message>(
    internalChannel: SendChannel<Message>,
    name: String,
    val parentScope: CoroutineScope
) : MActorRef<Message>(internalChannel, name) {


    // set later
    lateinit var ownScope: CoroutineScope
}

@OptIn(ObsoleteCoroutinesApi::class)
sealed class Behavior<in T> {


    fun ActorScope<@UnsafeVariance T>.stopChild(actorRef: MActorRef<*>) {
        when (actorRef) {
            is ChildActorRef -> {
                val parentScope = actorRef.parentScope
                if (this === parentScope) {
                    actorRef.ownScope.cancel()
                } else {
                    throw IllegalArgumentException("actor ${actorRef.name} is not child of this actor")
                }
            }

            is TopLevelActorRef -> {
                throw IllegalArgumentException("can not stop top level actor")
            }
        }
    }

}

@OptIn(ObsoleteCoroutinesApi::class)
abstract class AbstractBehaviour<T>(protected val scope: ActorScope<T>) : Behavior<T>() {
    abstract suspend fun onReceive(message: T): Behavior<T>
}

internal class TimerBehavior<T>(val timerFunc: suspend (TimerManData<T>) -> Behavior<T>) : Behavior<T>()

@OptIn(ObsoleteCoroutinesApi::class)
internal class SetUpBehavior<T>(val factory: suspend (ActorScope<T>) -> Behavior<T>) : Behavior<T>()

internal object O1 : Behavior<Any>()

internal object O2 : Behavior<Any>()