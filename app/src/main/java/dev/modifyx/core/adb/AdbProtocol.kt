package dev.modifyx.core.adb

/**
 * The ADB wire protocol, implemented here instead of depending on an external app.
 *
 * Every message is a 24-byte header (command, two arguments, payload length, payload
 * checksum, magic) followed by the payload. This object holds only pure functions so the
 * framing can be unit-tested without a device.
 */
object AdbProtocol {

    const val VERSION = 0x01000000

    const val CNXN = 0x4e584e43 // "CNXN"
    const val AUTH = 0x48545541 // "AUTH"
    const val OPEN = 0x4e45504f // "OPEN"
    const val OKAY = 0x59414b4f // "OKAY"
    const val CLSE = 0x45534c43 // "CLSE"
    const val WRTE = 0x45545257 // "WRTE"

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    const val HEADER_SIZE = 24
    const val MAX_PAYLOAD = 256 * 1024
    const val EXIT_MARKER = "__MX_EXIT__:"

    data class Header(val command: Int, val arg0: Int, val arg1: Int, val length: Int)

    data class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    fun encode(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
        require(data.size <= MAX_PAYLOAD) { "payload ${data.size} exceeds $MAX_PAYLOAD" }
        val out = ByteArray(HEADER_SIZE + data.size)
        writeInt(out, 0, command)
        writeInt(out, 4, arg0)
        writeInt(out, 8, arg1)
        writeInt(out, 12, data.size)
        writeInt(out, 16, checksum(data))
        writeInt(out, 20, command.inv())
        System.arraycopy(data, 0, out, HEADER_SIZE, data.size)
        return out
    }

    /** Returns null when the header is truncated or its magic does not match. */
    fun decodeHeader(header: ByteArray): Header? {
        if (header.size < HEADER_SIZE) return null
        val command = readInt(header, 0)
        val arg0 = readInt(header, 4)
        val arg1 = readInt(header, 8)
        val length = readInt(header, 12)
        val magic = readInt(header, 20)
        if (magic != command.inv()) return null
        if (length < 0 || length > MAX_PAYLOAD) return null
        return Header(command, arg0, arg1, length)
    }

    /** ADB's checksum is the unsigned byte sum, truncated to 32 bits. */
    fun checksum(data: ByteArray): Int {
        var sum = 0
        for (byte in data) sum += byte.toInt() and 0xFF
        return sum
    }

    fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    fun readInt(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)

    /**
     * Separates the sentinel exit line from command output. Returns -1 when the shell
     * stream ended without one, which the caller reports instead of guessing success.
     */
    fun splitExit(raw: String): Pair<String, Int> {
        val index = raw.lastIndexOf(EXIT_MARKER)
        if (index < 0) return raw to -1
        val code = raw.substring(index + EXIT_MARKER.length)
            .trim()
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.toIntOrNull() ?: -1
        return raw.substring(0, index) to code
    }

    fun shellCommand(command: String): ByteArray = "shell:$command\u0000".toByteArray(Charsets.UTF_8)

    fun hostBanner(): ByteArray = "host::features=cmd,shell_v2\u0000".toByteArray(Charsets.UTF_8)
}
