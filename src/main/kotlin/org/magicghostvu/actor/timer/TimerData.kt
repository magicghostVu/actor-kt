package org.magicghostvu.actor.timer

import kotlinx.coroutines.Job

sealed class TimerData(private val key: Any, val job: Job, private val timerMan: TimerManData<*>) {
    public fun cancel() {
        timerMan.removeKey(key, true)
    }
}

// khi message đến/hoặc quá hạn thì xoá
class SingleTimerData(key: Any, job: Job, timerMan: TimerManData<*>) : TimerData(key, job, timerMan)


// xoá khi bị đè hoặc là bị huỷ
class PeriodicTimerData(key: Any, job: Job, timerMan: TimerManData<*>) : TimerData(key, job, timerMan)