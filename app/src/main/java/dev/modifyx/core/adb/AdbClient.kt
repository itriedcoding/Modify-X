package dev.modifyx.core.adb

import android.os.SystemClock
import dev.modifyx.core.ShellResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * A small ADB host client: connects to adbd, authenticates with the app's own RSA key and
 * runs `shell:` services. Nothing is simulated — a failure to reach adbd is reported as
 * such, and every command returns adbd's real output and exit status.
 */
class AdbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val keys: AdbKeys,
) {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var localId = 1

    val address: String get() = "$host:$port"

    @Synchronized
    fun isConnected(): Boolean {
        val current = socket ?: return false
        return current.isConnected && !current.isClosed
    }

    /**
     * Connects and authenticates. Returns null on success or a message explaining the
     * failure. adbd shows an authorisation dialog for a new key, so this waits for it.
     */
    @Synchronized
    fun connect(timeoutMs: Int = 20_000): String? {
        close()
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            val created = Socket()
            created.connect(InetSocketAddress(host, port), 5_000)
            created.tcpNoDelay = true
            created.soTimeout = 5_000
            socket = created
            output = BufferedOutputStream(created.getOutputStream())
            input = BufferedInputStream(created.getInputStream())

            send(AdbProtocol.CNXN, AdbProtocol.VERSION, AdbProtocol.MAX_PAYLOAD, AdbProtocol.hostBanner())

            var sawAuthRequest = false
            while (System.currentTimeMillis() < deadline) {
                val message = readMessage(deadline) ?: return "adbd closed the connection during authentication."
                when (message.command) {
                    AdbProtocol.CNXN -> return null
                    AdbProtocol.AUTH -> when (message.arg0) {
                        AdbProtocol.AUTH_TOKEN -> {
                            sawAuthRequest = true
                            send(AdbProtocol.AUTH, AdbProtocol.AUTH_SIGNATURE, 0, keys.sign(message.data))
                        }
                        // adbd is waiting for the user to accept the fingerprint on screen.
                        AdbProtocol.AUTH_SIGNATURE, AdbProtocol.AUTH_RSAPUBLICKEY -> Thread.sleep(400)
                        else -> return "adbd rejected this device's key (auth type ${message.arg0})."
                    }
                    AdbProtocol.CLSE -> return "adbd closed the session."
                    else -> Unit
                }
            }
            return if (sawAuthRequest) {
                "adbd did not accept the key in time. Approve the debugging prompt on the device and retry."
            } else {
                "Timed out talking to adbd on $address."
            }
        } catch (timeout: SocketTimeoutException) {
            close()
            return "adbd did not answer on $address within ${timeoutMs / 1000}s."
        } catch (error: Throwable) {
            close()
            return error.message ?: "Could not reach adbd on $address."
        }
    }

    @Synchronized
    fun shell(command: String, timeoutMs: Long): ShellResult {
        val began = SystemClock.elapsedRealtime()
        fun fail(message: String, exitCode: Int = 127): ShellResult = ShellResult(
            command = command,
            stdout = "",
            stderr = message,
            exitCode = exitCode,
            durationMs = SystemClock.elapsedRealtime() - began,
        )

        if (!isConnected()) {
            connect()?.let { return fail(it) }
        }

        val streamId = localId++
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            send(AdbProtocol.OPEN, streamId, 0, AdbProtocol.shellCommand(command))
            val text = StringBuilder()
            var remoteId = 0

            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    runCatching { send(AdbProtocol.CLSE, streamId, remoteId, ByteArray(0)) }
                    return fail("Timed out after ${timeoutMs / 1000}s", ShellResult.EXIT_TIMEOUT)
                }
                val message = readMessage(deadline) ?: return fail("adbd closed the connection.")
                when (message.command) {
                    AdbProtocol.OKAY -> if (message.arg1 == streamId) remoteId = message.arg0
                    AdbProtocol.WRTE -> if (message.arg1 == streamId) {
                        text.append(String(message.data, Charsets.UTF_8))
                        send(AdbProtocol.OKAY, streamId, message.arg0, ByteArray(0))
                    }
                    AdbProtocol.CLSE -> if (message.arg1 == streamId) {
                        runCatching { send(AdbProtocol.CLSE, streamId, message.arg0, ByteArray(0)) }
                        break
                    }
                    else -> Unit
                }
            }

            val (rawOutput, exitCode) = AdbProtocol.splitExit(text.toString())
            return ShellResult(
                command = command,
                stdout = rawOutput.trimEnd(),
                stderr = "",
                exitCode = exitCode,
                durationMs = SystemClock.elapsedRealtime() - began,
            )
        } catch (error: Throwable) {
            close()
            return fail(error.message ?: "ADB shell stream failed")
        }
    }

    @Synchronized
    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val stream = output ?: throw IOException("not connected to adbd")
        stream.write(AdbProtocol.encode(command, arg0, arg1, data))
        stream.flush()
    }

    private fun readMessage(deadline: Long): AdbProtocol.Message? {
        val source = input ?: return null

        val header = ByteArray(AdbProtocol.HEADER_SIZE)
        if (!readFully(source, header, deadline)) return null
        val decoded = AdbProtocol.decodeHeader(header) ?: return null
        val data = ByteArray(decoded.length)
        if (decoded.length > 0 && !readFully(source, data, deadline)) return null
        return AdbProtocol.Message(decoded.command, decoded.arg0, decoded.arg1, data)
    }

    /**
     * Reads until the buffer is full, tolerating silent gaps: a command such as `pm install`
     * can produce no output for a long time without meaning that adbd died. Partial reads
     * are preserved, so a timeout in the middle of a header cannot misalign the stream.
     */
    private fun readFully(source: InputStream, buffer: ByteArray, deadline: Long): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) throw SocketTimeoutException("command deadline reached")
            socket?.soTimeout = remaining.coerceIn(250L, 20_000L).toInt()
            val read = try {
                source.read(buffer, offset, buffer.size - offset)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (read < 0) return false
            offset += read
        }
        return true
    }
}
