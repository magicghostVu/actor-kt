package org.magicghostvu.actor

import org.magicghostvu.actor.timer.TimerManData

open class Behavior<in T>

abstract class AbstractBehavior<T>() : Behavior<T>() {
    abstract suspend fun onReceive(message: T): Behavior<T>
}

class TimerBehavior<T>(val timerFunc: (TimerManData<T>) -> Behavior<T>) : Behavior<T>()