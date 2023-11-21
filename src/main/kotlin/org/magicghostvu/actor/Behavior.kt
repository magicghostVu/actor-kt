package org.magicghostvu.actor

import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.SendChannel
import org.magicghostvu.actor.timer.TimerManData
import org.magicghostvu.mlogger.ActorLogger


class MActorRef<in Message>(private val internalChannel: SendChannel<Message>, private val name:String) {
    public suspend fun tell(message: Message) {
        try {
            internalChannel.send(message)
        } catch (e: Exception) {
            ActorLogger.logger.error("err while send message $message to actor $name", e)
        }
    }
}

sealed class Behavior<in T> {

}

@OptIn(ObsoleteCoroutinesApi::class)
abstract class AbstractBehaviour<T>(protected val scope: ActorScope<T>) : Behavior<T>() {
    abstract suspend fun onReceive(message: T): Behavior<T>
}

internal class TimerBehavior<T>(val timerFunc: suspend (TimerManData<T>) -> Behavior<T>) : Behavior<T>()

@OptIn(ObsoleteCoroutinesApi::class)
internal class SetUpBehavior<T>(val factory: suspend (ActorScope<T>) -> Behavior<T>) : Behavior<T>()

internal object O1: Behavior<Any>()

internal object O2: Behavior<Any>()