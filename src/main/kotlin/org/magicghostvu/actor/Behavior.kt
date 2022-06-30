package org.magicghostvu.actor

import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ActorScope
import kotlinx.coroutines.channels.SendChannel
import org.magicghostvu.actor.timer.TimerManData


class MActorRef<in Message>(private val internalChannel: SendChannel<Message>) {
    public suspend fun tell(message: Message) {
        internalChannel.send(message)
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