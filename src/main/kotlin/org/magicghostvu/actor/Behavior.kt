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
            ActorLogger.logger.error("err while send message to actor $name")
        }
    }
}

open class Behavior<in T> {

}

@OptIn(ObsoleteCoroutinesApi::class)
abstract class AbstractBehaviour<T>(protected val scope: ActorScope<T>) : Behavior<T>() {
    abstract suspend fun onReceive(message: T): Behavior<T>
}

class TimerBehavior<T>(val timerFunc: suspend (TimerManData<T>) -> Behavior<T>) : Behavior<T>()

@OptIn(ObsoleteCoroutinesApi::class)
class SetUpBehavior<T>(val factory: suspend (ActorScope<T>) -> Behavior<T>) : Behavior<T>()