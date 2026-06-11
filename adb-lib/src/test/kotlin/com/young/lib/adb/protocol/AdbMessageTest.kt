package com.young.lib.adb.protocol

import com.young.lib.adb.AdbCommand
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessageTest {

    private val MAGIC_XOR = 0xffffffff.toInt() // command ^ this

    @Test
    fun `create 24-byte header with command only`() {
        val header = AdbMessage.create(
            command = AdbCommand.CNXN,
            arg0 = 0x01000001,
            arg1 = 1024 * 1024
        )

        assertEquals(24, header.size)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(AdbCommand.CNXN, buffer.getInt())
        assertEquals(0x01000001, buffer.getInt())
        assertEquals(1024 * 1024, buffer.getInt())
        assertEquals(0, buffer.getInt())  // data_length = 0
        assertEquals(0, buffer.getInt())  // data_crc32 = 0
        assertEquals(AdbCommand.CNXN xor MAGIC_XOR, buffer.getInt())
    }

    @Test
    fun `create header with payload metadata`() {
        val payload = "host::".toByteArray(Charsets.UTF_8)
        val header = AdbMessage.create(
            command = AdbCommand.CNXN,
            arg0 = 0x01000001,
            arg1 = 1024 * 1024,
            dataLength = payload.size,
            dataCrc32 = 0
        )

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        assertEquals(payload.size, buffer.getInt())
        assertEquals(0, buffer.getInt())
    }

    @Test
    fun `parse valid CNXN header`() {
        val header = AdbMessage.create(
            command = AdbCommand.CNXN,
            arg0 = 0x01000001,
            arg1 = 1024 * 1024
        )
        val parsed = AdbMessage.parse(header)

        assertEquals(AdbCommand.CNXN, parsed.command)
        assertEquals(0x01000001, parsed.arg0)
        assertEquals(1024 * 1024, parsed.arg1)
        assertEquals(0, parsed.dataLength)
        assertEquals(0, parsed.dataCrc32)
        assertEquals(AdbCommand.CNXN xor MAGIC_XOR, parsed.magic)
    }

    @Test
    fun `parse all command types`() {
        val commands = listOf(
            AdbCommand.SYNC,
            AdbCommand.CNXN,
            AdbCommand.AUTH,
            AdbCommand.OPEN,
            AdbCommand.OKAY,
            AdbCommand.CLSE,
            AdbCommand.WRTE
        )

        for (cmd in commands) {
            val header = AdbMessage.create(command = cmd, arg0 = 1, arg1 = 2)
            val parsed = AdbMessage.parse(header)
            assertEquals("Failed for command 0x%x".format(cmd), cmd, parsed.command)
            assertEquals(1, parsed.arg0)
            assertEquals(2, parsed.arg1)
            assertEquals(cmd xor MAGIC_XOR, parsed.magic)
        }
    }

    @Test
    fun `SYNC is identified as sync`() {
        val header = AdbMessage.create(command = AdbCommand.SYNC)
        val parsed = AdbMessage.parse(header)
        assertTrue(parsed.isSync)
    }

    @Test
    fun `non-sync commands are not sync`() {
        val commands = listOf(AdbCommand.CNXN, AdbCommand.AUTH, AdbCommand.OPEN, AdbCommand.OKAY)
        for (cmd in commands) {
            val header = AdbMessage.create(command = cmd)
            val parsed = AdbMessage.parse(header)
            assertFalse("Command 0x%x should not be sync".format(cmd), parsed.isSync)
        }
    }

    @Test
    fun `magic mismatch throws exception`() {
        val bogus = ByteArray(24)
        ByteBuffer.wrap(bogus).order(ByteOrder.LITTLE_ENDIAN).putInt(AdbCommand.AUTH)
        try {
            AdbMessage.parse(bogus)
            fail("Expected IllegalArgumentException for magic mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Magic") == true)
        }
    }

    @Test
    fun `CRC32 zero always passes`() {
        val data = "anything".toByteArray()
        assertTrue(AdbMessage.verifyCrc32(data, 0))
    }

    @Test
    fun `CRC32 non-zero mismatch`() {
        val data = "original".toByteArray()
        assertFalse(AdbMessage.verifyCrc32(data, 0xDEAD_BEEF.toInt()))
    }

    @Test
    fun `AUTH command with different types`() {
        val tokenHeader = AdbMessage.create(command = AdbCommand.AUTH, arg0 = AdbCommand.AUTH_TOKEN)
        assertEquals(AdbCommand.AUTH_TOKEN, AdbMessage.parse(tokenHeader).arg0)

        val sigHeader = AdbMessage.create(command = AdbCommand.AUTH, arg0 = AdbCommand.AUTH_SIGNATURE)
        assertEquals(AdbCommand.AUTH_SIGNATURE, AdbMessage.parse(sigHeader).arg0)

        val pubHeader = AdbMessage.create(command = AdbCommand.AUTH, arg0 = AdbCommand.AUTH_RSA_PUBLIC)
        assertEquals(AdbCommand.AUTH_RSA_PUBLIC, AdbMessage.parse(pubHeader).arg0)
    }

    @Test
    fun `OPEN command with service name`() {
        val service = "shell:ls"
        val payload = service.toByteArray()
        val header = AdbMessage.create(
            command = AdbCommand.OPEN,
            arg0 = 42,
            dataLength = payload.size
        )

        val parsed = AdbMessage.parse(header)
        assertEquals(AdbCommand.OPEN, parsed.command)
        assertEquals(42, parsed.arg0)
        assertEquals(0, parsed.arg1)
        assertEquals(payload.size, parsed.dataLength)
    }
}
