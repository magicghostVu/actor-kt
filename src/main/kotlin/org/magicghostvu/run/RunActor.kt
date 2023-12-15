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
import org.slf4j.Logger

sealed interface ParentMessage


object CreateSomeChild : ParentMessage

object StopChildren : ParentMessage

@OptIn(ObsoleteCoroutinesApi::class)
class ParentActor(actorScope: ActorScope<ParentMessage>) : AbstractBehaviour<ParentMessage>(actorScope) {


    private val logger = ActorLogger.logger
    private lateinit var child: MActorRef<*>


    init {
        logger.info("my scope parent is {}", scope)
    }

    override suspend fun onReceive(message: ParentMessage): Behavior<ParentMessage> {
        return when (message) {
            CreateSomeChild -> {
                child = scope.spawnChild(
                    "some name"
                ) {
                    ChildActor.setup()
                }
                Behaviors.same()
            }

            StopChildren -> {
                scope.stopChild(child)
                //logger.info("all child stopped")
                //(child as MActorRef<ChildMessage>).tell(Cm1)

                Behaviors.same()
            }
        }
    }

    companion object {
        fun setup(): Behavior<ParentMessage> {
            return Behaviors.setUp {
                ParentActor(it)
            }
        }
    }
}


sealed interface ChildMessage

object Cm1 : ChildMessage

@OptIn(ObsoleteCoroutinesApi::class)
class ChildActor(scope: ActorScope<ChildMessage>) : AbstractBehaviour<ChildMessage>(scope) {
    private val logger: Logger = ActorLogger.logger

    init {

        logger.info("child scope inside is {}", scope)
    }

    override suspend fun onReceive(message: ChildMessage): Behavior<ChildMessage> {
        return when (message) {
            Cm1 -> {
                logger.info("cm1 come")
                Behaviors.same()
            }
        }
    }

    companion object {
        fun setup(): Behavior<ChildMessage> {
            return Behaviors.setUp {
                ChildActor(it)
            }
        }
    }
}

fun main(arr: Array<String>) {
    runBlocking {
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val pRef = newScope.spawnNew("parent") {
            ParentActor.setup()
        }

        pRef.tell(CreateSomeChild)



        delay(2000)


        pRef.tell(StopChildren)

        delay(1000)
        pRef.tell(StopChildren)

        launch {
            while (true) {
                delay(1000)
            }
        }
    }
}