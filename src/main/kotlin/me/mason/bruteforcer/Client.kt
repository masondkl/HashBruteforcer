package me.mason.bruteforcer

import com.github.masondkl.plinth.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.time.Duration.Companion.INFINITE

const val CLI_OUT_RESULT = 0.toByte()

const val CLI_IN_TASK = 0.toByte()
const val CLI_IN_END = 1.toByte()

suspend fun main() {
    val address = InetSocketAddress("127.0.0.1", 9999)
    val service = Executors.newCachedThreadPool()
    val dispatcher = service.asCoroutineDispatcher()
    val group = withContext(Dispatchers.IO) { AsynchronousChannelGroup.withThreadPool(service) }
    val found = AtomicReference(charArrayOf())
    val task = AtomicInteger(-1)
    val progresses = ConcurrentHashMap<Int, Int>()
    CoroutineScope(Dispatchers.Default).launch {
        while(true) {
            if (found.get().isEmpty()) print("[${task.get()}] ${
                    progresses
                        .map { (k, v) -> k to "$v/16" }
                        .sortedBy { (k, _) -> k }
                        .joinToString(", ") { (_, v) -> v }
            }\r")
            else print("[${task.get()}] match: ${String(found.get())}\r")
            delay(100)
        }
    }
    for (i in 0..<8) {
        CoroutineScope(dispatcher).launch {
            val sent = AtomicBoolean(false)
            var job: Job? = null
            client(address, group) {
                val writes = dispatchWrites(dispatcher)
                try {
                    while (open.get()) {
                        yield()
                        when (byte()) {
                            CLI_IN_TASK -> {
                                found.safeSet(charArrayOf()) { a, b -> a.contentEquals(b) }
                                sent.safeSet(false)
                                val taskId = int()
                                val hash = bytes(int())
                                val salt = bytes(int())
                                val offset = int()
                                val count = int()
                                var prev = 0
                                if (taskId != task.get()) {
                                    progresses.clear()
                                    task.safeSet(taskId)
                                }
                                job?.cancel()
                                progresses[i] = 0
                                job = CoroutineScope(dispatcher).launch job@{
                                    var equal = false
                                    val predict = CharArray(LENGTH)
                                    for (it in 0..<count) {
                                        if (!isActive) {
                                            return@job
                                        }
                                        yield()
                                        val progress = floor(it / (count / 16.0)).toInt()
                                        if (prev != progress) {
                                            prev = progress
                                            progresses[i] = progress
                                        }
                                        var char = offset + it
                                        (0..<LENGTH).forEach { charIdx ->
                                            predict[charIdx] = VALID[char % VALID.size].toInt().toChar().also {
                                                char /= VALID.size
                                            }
                                        }
                                        val predictHash = hash(salt, predict)
                                        equal = predictHash.contentEquals(hash)
                                        if (equal) break
                                    }
                                    if (equal) found.safeSet(predict) { a, b -> a.contentEquals(b) }
                                    val copy = if (equal) predict.copyOf() else null
                                    sent.safeSet(channel.trySend {
                                        packet(CLI_OUT_RESULT) {
                                            int(taskId)
                                            int(copy?.size ?: 0)
                                            if (copy != null) bytes(copy)
                                        }
                                    }.isSuccess)
                                }
                            }
                            CLI_IN_END -> {
                                job?.cancel()
                            }
                        }
                    }
                    if (!sent.get()) {
                        packet(CLI_OUT_RESULT) { int(0) }
                    }
                } catch (err: Exception) {
                    if (writes.isActive) writes.cancel()
                    if (job?.isActive == true) job?.cancel()
                }
            }
        }
    }
    delay(INFINITE)
}