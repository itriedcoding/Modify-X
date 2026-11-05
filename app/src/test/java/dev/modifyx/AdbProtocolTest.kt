package dev.modifyx

import dev.modifyx.core.adb.AdbProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbProtocolTest {

    @Test
    fun headerRoundTripsThroughEncodeAndDecode() {
        val payload = "shell:pm list packages\u0000".toByteArray()
        val encoded = AdbProtocol.encode(AdbProtocol.OPEN, 7, 0, payload)
        assertEquals(AdbProtocol.HEADER_SIZE + payload.size, encoded.size)

        val header = AdbProtocol.decodeHeader(encoded.copyOf(AdbProtocol.HEADER_SIZE))
        assertNotNull(header)
        assertEquals(AdbProtocol.OPEN, header!!.command)
        assertEquals(7, header.arg0)
        assertEquals(0, header.arg1)
        assertEquals(payload.size, header.length)
    }

    @Test
    fun corruptedHeaderIsRejected() {
        val encoded = AdbProtocol.encode(AdbProtocol.CNXN, AdbProtocol.VERSION, AdbProtocol.MAX_PAYLOAD, ByteArray(0))
        val broken = encoded.copyOf()
        // Flip one bit in the command so it no longer matches the magic field.
        broken[0] = (broken[0].toInt() xor 0x01).toByte()
        assertNull(AdbProtocol.decodeHeader(broken))
        assertNull(AdbProtocol.decodeHeader(ByteArray(10)))
    }

    @Test
    fun payloadLengthAboveTheLimitIsRejected() {
        val header = ByteArray(AdbProtocol.HEADER_SIZE)
        val command = AdbProtocol.WRTE
        AdbProtocol.writeInt(header, 0, command)
        AdbProtocol.writeInt(header, 12, AdbProtocol.MAX_PAYLOAD + 1)
        AdbProtocol.writeInt(header, 20, command.inv())
        assertNull(AdbProtocol.decodeHeader(header))
    }

    @Test
    fun checksumSumsUnsignedBytes() {
        assertEquals(0, AdbProtocol.checksum(ByteArray(0)))
        assertEquals(255, AdbProtocol.checksum(byteArrayOf(0xFF.toByte())))
        assertEquals(510, AdbProtocol.checksum(byteArrayOf(0xFF.toByte(), 0xFF.toByte())))
        assertEquals(3, AdbProtocol.checksum(byteArrayOf(1, 1, 1)))
    }

    @Test
    fun exitMarkerIsSeparatedFromOutput() {
        val raw = "package:dev.modifyx\nsecond line\n__MX_EXIT__:0\n"
        val (output, exit) = AdbProtocol.splitExit(raw)
        assertEquals("package:dev.modifyx\nsecond line\n", output)
        assertEquals(0, exit)
    }

    @Test
    fun failingCommandKeepsItsExitCode() {
        val (output, exit) = AdbProtocol.splitExit("error: not found\n__MX_EXIT__:127\n")
        assertEquals("error: not found\n", output)
        assertEquals(127, exit)
    }

    @Test
    fun missingMarkerReportsUnknownInsteadOfSuccess() {
        val (output, exit) = AdbProtocol.splitExit("truncated output")
        assertEquals("truncated output", output)
        assertEquals(-1, exit)
    }

    @Test
    fun shellRequestsAreNulTerminatedLikeAdbExpects() {
        val payload = AdbProtocol.shellCommand("pm list packages")
        assertEquals("shell:pm list packages\u0000", String(payload))
        assertTrue(String(AdbProtocol.hostBanner()).endsWith("\u0000"))
        assertTrue(String(AdbProtocol.hostBanner()).startsWith("host::"))
    }

    @Test
    fun intEncodingIsLittleEndian() {
        val buffer = ByteArray(4)
        AdbProtocol.writeInt(buffer, 0, 0x01020304)
        assertEquals(0x04.toByte(), buffer[0])
        assertEquals(0x01.toByte(), buffer[3])
        assertEquals(0x01020304, AdbProtocol.readInt(buffer, 0))
    }
}
