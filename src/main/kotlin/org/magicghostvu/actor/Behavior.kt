package org.magicghostvu.actor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.magicghostvu.actor.timer.TimerManData


class MActorRef<in Message>(private val internalChannel: SendChannel<Message>) {
    public suspend fun tell(message: Message) {
        internalChannel.send(message)
    }
}

open class Behavior<in T> {

}

abstract class AbstractBehaviour<T>(protected val scope: CoroutineScope) : Behavior<T>() {
    abstract suspend fun onReceive(message: T): Behavior<T>
}

class TimerBehavior<T>(val timerFunc: suspend (TimerManData<T>) -> Behavior<T>) : Behavior<T>()