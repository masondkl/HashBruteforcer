package me.mason.bruteforcer

import kotlinx.coroutines.delay
import com.github.masondkl.plinth.*
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.Duration.Companion.minutes

//TODO: make these program args and make a main to run both client or server
const val LENGTH = 4
val SALT = ByteArray(32).also { SecureRandom().nextBytes(it) }
val VALID = (97..122).map { it.toByte() }.toByteArray()
val TIMEOUT = 1.minutes

fun hash(salt: ByteArray, secret: CharArray): ByteArray {
    val spec = PBEKeySpec(secret, salt, 1024, 512)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
}

suspend fun Write.packet(type: Byte, block: suspend Write.() -> (Unit) = { }) {
    val measured = (1 + MeasuredWrite().apply { block() }.size) //can fail if block performs a read
    val buffered = BufferedWrite(measured).apply {
        byte(type)
        block()
    }
    buffer(buffered.buffer.flip())
}

suspend fun AtomicBoolean.safeSet(value: Boolean) {
    while(!compareAndSet(get(), value)) { delay(1) }
}
suspend fun AtomicInteger.safeSet(value: Int) {
    while(!compareAndSet(get(), value)) { delay(1) }
}
suspend fun <T> AtomicReference<T>.safeSet(value: T, cmp: (T, T) -> (Boolean)) {
    while(!cmp(get(), getAndSet(value))) { delay(1) }
}

fun Int.pow(pow: Int) = (0..pow).fold(this) { acc, it ->
    if (it == 0) 1 else acc * this
}