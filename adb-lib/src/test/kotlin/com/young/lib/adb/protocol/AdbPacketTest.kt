package com.young.lib.adb.protocol

import com.young.lib.adb.AdbCommand
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbPacketTest {

    @Test
    fun `CNXN packet toByteArray round trip`() {
        val banner = "host::"
        val packet = AdbPacket.connect(0x01000001, 1024 * 1024, banner)
        val data = packet.toByteArray()

        val bannerBytes = banner.toByteArray()
        // 24 header + payload
        assertEquals(24 + bannerBytes.size, data.size)

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.CNXN, buffer.getInt())
        assertEquals(0x01000001, buffer.getInt())
        assertEquals(1024 * 1024, buffer.getInt())
        assertEquals(bannerBytes.size, buffer.getInt())
        buffer.getInt() // crc32
        assertEquals(AdbCommand.CNXN xor 0xffffffff.toInt(), buffer.getInt())

        // payload
        val actualPayload = ByteArray(bannerBytes.size)
        buffer.get(actualPayload)
        assertArrayEquals(bannerBytes, actualPayload)
    }

    @Test
    fun `AUTH signal packet`() {
        val token = ByteArray(20) { it.toByte() }
        val packet = AdbPacket.auth(AdbCommand.AUTH_SIGNATURE, token)
        val data = packet.toByteArray()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.AUTH, buffer.getInt())
        assertEquals(2, buffer.getInt())       // SIGNATURE type
        assertEquals(0, buffer.getInt())        // arg1 = 0
        assertEquals(20, buffer.getInt())       // data_length
    }

    @Test
    fun `OPEN packet`() {
        val service = "shell:".toByteArray()
        val packet = AdbPacket.open(1, "shell:")
        val data = packet.toByteArray()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.OPEN, buffer.getInt())
        assertEquals(1, buffer.getInt())        // local_id
        assertEquals(0, buffer.getInt())         // arg1 = 0 for OPEN
        assertEquals(service.size, buffer.getInt())
    }

    @Test
    fun `OKAY packet`() {
        val packet = AdbPacket.okay(1, 10)
        val data = packet.toByteArray()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.OKAY, buffer.getInt())
        assertEquals(1, buffer.getInt())   // local_id
        assertEquals(10, buffer.getInt())  // remote_id
        assertEquals(0, buffer.getInt())   // data_length = 0
    }

    @Test
    fun `WRTE packet`() {
        val payload = "hello world".toByteArray()
        val packet = AdbPacket.write(1, 10, payload)
        val data = packet.toByteArray()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.WRTE, buffer.getInt())
        assertEquals(1, buffer.getInt())
        assertEquals(10, buffer.getInt())
        assertEquals(payload.size, buffer.getInt())
    }

    @Test
    fun `CLSE packet`() {
        val packet = AdbPacket.close(1, 10)
        val data = packet.toByteArray()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbCommand.CLSE, buffer.getInt())
        assertEquals(1, buffer.getInt())
        assertEquals(10, buffer.getInt())
        assertEquals(0, buffer.getInt())  // data_length = 0
    }

    @Test
    fun `CLSE with zero remote_id`() {
        // 根据 AOSP protocol.txt，OPEN 失败时可以用 local_id=0
        val packet = AdbPacket.close(0, 0)
        assertEquals(AdbCommand.CLSE, packet.command)
        assertEquals(0, packet.arg0)
        assertEquals(0, packet.arg1)
    }

    @Test
    fun `parse packet from raw bytes`() {
        val original = AdbPacket.write(3, 7, "test".toByteArray())
        val raw = original.toByteArray()

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.getInt()
        val arg0 = buffer.getInt()
        val arg1 = buffer.getInt()
        val len = buffer.getInt()
        buffer.getInt() // crc32
        buffer.getInt() // magic

        val payload = ByteArray(len)
        buffer.get(payload)

        assertEquals(AdbCommand.WRTE, command)
        assertEquals(3, arg0)
        assertEquals(7, arg1)
        assertEquals("test", String(payload))
    }
}
