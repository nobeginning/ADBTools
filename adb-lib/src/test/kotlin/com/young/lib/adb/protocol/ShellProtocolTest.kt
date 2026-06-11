package com.young.lib.adb.protocol

import org.junit.Assert.*
import org.junit.Test

class ShellProtocolTest {

    @Test
    fun `parse stdout packet`() {
        val data = "Hello\n".toByteArray()
        val packet = buildShellPacket(1, data)

        val result = ShellProtocol.parsePacket(packet)

        assertNotNull(result)
        assertTrue(result is ShellData.Stdout)
        assertEquals("Hello\n", (result as ShellData.Stdout).text)
    }

    @Test
    fun `parse stderr packet`() {
        val data = "error\n".toByteArray()
        val packet = buildShellPacket(2, data)

        val result = ShellProtocol.parsePacket(packet)

        assertNotNull(result)
        assertTrue(result is ShellData.Stderr)
        assertEquals("error\n", (result as ShellData.Stderr).text)
    }

    @Test
    fun `parse exit with code zero`() {
        val packet = byteArrayOf(3, 0)

        val result = ShellProtocol.parsePacket(packet)

        assertNotNull(result)
        assertTrue(result is ShellData.Exit)
        assertEquals(0, (result as ShellData.Exit).code)
    }

    @Test
    fun `stdin is ignored on parse`() {
        val packet = buildShellPacket(0, "input".toByteArray())

        val result = ShellProtocol.parsePacket(packet)

        assertNull(result)
    }

    @Test
    fun `parse multiple packets in one WRTE`() {
        val p1 = buildShellPacket(1, "line1\n".toByteArray())
        val p2 = buildShellPacket(1, "line2\n".toByteArray())
        val p3 = byteArrayOf(3, 0)
        val combined = p1 + p2 + p3

        val results = ShellProtocol.parsePackets(combined)

        assertEquals(3, results.size)
        assertTrue(results[0] is ShellData.Stdout)
        assertTrue(results[1] is ShellData.Stdout)
        assertTrue(results[2] is ShellData.Exit)
    }

    @Test
    fun `parse empty payload returns null`() {
        assertNull(ShellProtocol.parsePacket(ByteArray(0)))
    }

    @Test
    fun `parse empty list for empty data`() {
        assertEquals(0, ShellProtocol.parsePackets(ByteArray(0)).size)
    }

    @Test
    fun `build stdin check format`() {
        val input = "ls -la\n".toByteArray()
        val packet = ShellProtocol.buildStdin(input)

        assertEquals(0.toByte(), packet[0])
    }

    @Test
    fun `build stdin empty input`() {
        val packet = ShellProtocol.buildStdin(ByteArray(0))
        assertEquals(0.toByte(), packet[0])
    }

    @Test
    fun `build stdin has proper varint length`() {
        val input = "echo hello\n".toByteArray()
        val built = ShellProtocol.buildStdin(input)

        assertEquals(0.toByte(), built[0])
        assertTrue(built.size >= 2 + input.size)
    }

    @Test
    fun `mixed stdout and stderr in same WRTE`() {
        val p1 = buildShellPacket(1, "out1\n".toByteArray())
        val p2 = buildShellPacket(2, "err1\n".toByteArray())
        val p3 = buildShellPacket(1, "out2\n".toByteArray())
        val combined = p1 + p2 + p3

        val results = ShellProtocol.parsePackets(combined)

        assertEquals(3, results.size)
        assertTrue(results[0] is ShellData.Stdout)
        assertTrue(results[1] is ShellData.Stderr)
        assertTrue(results[2] is ShellData.Stdout)
    }

    private fun buildShellPacket(id: Byte, data: ByteArray): ByteArray {
        val lenBuffer = encodeVarint(data.size)
        val result = ByteArray(1 + lenBuffer.size + data.size)
        result[0] = id
        System.arraycopy(lenBuffer, 0, result, 1, lenBuffer.size)
        System.arraycopy(data, 0, result, 1 + lenBuffer.size, data.size)
        return result
    }

    private fun encodeVarint(value: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var v = value
        while (v >= 0x80) {
            out.write((v and 0x7f) or 0x80)
            v = v shr 7
        }
        out.write(v and 0x7f)
        return out.toByteArray()
    }
}
