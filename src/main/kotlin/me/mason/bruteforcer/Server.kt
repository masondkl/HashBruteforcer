package me.mason.bruteforcer

import com.github.masondkl.plinth.dispatchWrites
import com.github.masondkl.plinth.server
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource.Monotonic.markNow

const val SRV_IN_RESULT = 0.toByte()

const val SRV_OUT_TASK = 0.toByte()
const val SRV_OUT_END = 1.toByte()

private var TASK_EID = 0

suspend fun main() {
    val address = InetSocketAddress("127.0.0.1", 9999)
    val service = Executors.newCachedThreadPool()
    val dispatcher = service.asCoroutineDispatcher()
    val group = withContext(Dispatchers.IO) { AsynchronousChannelGroup.withThreadPool(service) }
    val taskId = AtomicInteger(0)
    val hash = AtomicReference(byteArrayOf())
    val result = AtomicReference(charArrayOf())
    val server = server(address, group, dispatcher) {
        onConnect {
            dispatchWrites(dispatcher)
            println("[$it] node connected")
            try {
                while (open.get()) {
                    yield()
                    when (byte()) {
                        SRV_IN_RESULT -> {
                            val resultTaskId = int()
                            val chars = bytes(int(), true)
                            if (resultTaskId != taskId.get()) continue
                            if (chars.isNotEmpty()) {
                                result.safeSet(chars) { a, b -> a.contentEquals(b) }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        onDisconnect {
            println("[$it] node disconnected")
        }
    }
    while(true) {
        println("press enter to dispatch task to all nodes...")
        withContext(Dispatchers.IO) {
            System.`in`.apply { read(); (0..<available()).forEach { _ -> read() } }
        }
        val secret = CharArray(LENGTH) { VALID.random().toInt().toChar() }
        println("secret: ${String(secret)}")
        taskId.safeSet(TASK_EID++)
        hash.safeSet(hash(SALT, secret)) { a, b -> a.contentEquals(b) }
        result.safeSet(charArrayOf()) { a, b -> a.contentEquals(b) }
        val connections = server.cardinality()
        if (connections == 0) {
            println("there are no nodes to dispatch to")
            continue
        }
        val combinations = VALID.size.pow(LENGTH)
        val per = combinations / connections
        var remainder = VALID.size.pow(LENGTH)
        var offset = 0
        var sent = 0
        server.forActive { id, connection ->
            val count =
                if (remainder < per) remainder.also { remainder = 0 }
                else per.also { remainder -= per }
            val offsetCopy = offset
            val hashCopy = hash.get().copyOf()
            if (connection.channel.trySend {
                packet(SRV_OUT_TASK) {
                    int(taskId.get())
                    int(hashCopy.size)
                    bytes(hashCopy)
                    int(offsetCopy)
                    int(count)
                }
            }.isSuccess) {
                println("dispatched to node $id")
                sent++
            }
            offset += per
        }
        if (connections != sent) {
            println("an error occurred")
            break
        }
        val start = markNow()
        while (result.get().isEmpty() && start.elapsedNow() < TIMEOUT) delay(100)
        if (result.get().isEmpty()) {
            println("unable to find result")
        } else {
            println("result: ${String(result.get())}")
        }
        server.forActive { _, connection ->
            connection.channel.trySend {
                packet(SRV_OUT_END)
            }
        }
    }
}