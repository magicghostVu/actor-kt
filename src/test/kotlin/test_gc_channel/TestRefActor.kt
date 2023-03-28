package test_gc_channel

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach

import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertNull


class Wrapper<T>(var value: T? = null)

class TestRefActor {
    @OptIn(ObsoleteCoroutinesApi::class)
    @Test
    internal fun testRefChannel() {
        val r = runBlocking {
            val strongRef = Wrapper<WeakReference<Any>>()
            val channel = actor<Int> {
                val newObject = Any()
                val weakRef = WeakReference(newObject)
                strongRef.value = weakRef
                println("created channel, weak ref val is ${weakRef.get()}")
            }
            launch {
                delay(500)
                channel.close()
                println("close channel")
            }
            delay(1000)
            val weakRef = strongRef.value
            weakRef ?: throw IllegalArgumentException()
            System.gc()
            weakRef.get()
        }
        assertNull(r)
    }

    @Test
    internal fun testChannelClose() {
        runBlocking {
            val c = Channel<Int>()

            launch {
                c.consumeEach {

                }
                // hàm consumer each hoặc for sẽ return khi channel bị close
                println("done channel")
            }

            launch {
                repeat(10) {
                    c.send(it)
                    delay(2000)
                }
                c.close()
            }
        }
    }
}