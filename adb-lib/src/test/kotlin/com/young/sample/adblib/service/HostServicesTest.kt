package com.young.sample.adblib.service

import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.DeviceState
import org.junit.Assert.*
import org.junit.Test

class HostServicesTest {

    @Test
    fun `build smart socket request`() {
        val service = "host:version"  // 12 chars
        val hexLen = "%04x".format(service.length)
        val request = (hexLen + service).toByteArray(Charsets.UTF_8)

        assertEquals("000chost:version", String(request))
    }

    @Test
    fun `build device list request`() {
        val service = "host:devices"  // 12 chars
        val hexLen = "%04x".format(service.length)
        val request = hexLen + service

        assertEquals("000chost:devices", request)
    }

    @Test
    fun `parse device list single device`() {
        val payload = "emulator-5554\tdevice\n".toByteArray()
        val devices = parseDeviceList(payload)

        assertEquals(1, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertEquals(DeviceState.DEVICE, devices[0].state)
    }

    @Test
    fun `parse device list multiple devices`() {
        val payload = "emulator-5554\tdevice\n0a1b2c3d\tdevice\nabc123\toffline\n".toByteArray()
        val devices = parseDeviceList(payload)

        assertEquals(3, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertEquals(DeviceState.DEVICE, devices[0].state)
        assertEquals("0a1b2c3d", devices[1].serial)
        assertEquals(DeviceState.DEVICE, devices[1].state)
        assertEquals("abc123", devices[2].serial)
        assertEquals(DeviceState.OFFLINE, devices[2].state)
    }

    @Test
    fun `parse device list unauthorized`() {
        val payload = "9a8b7c6d\tunauthorized\n".toByteArray()
        val devices = parseDeviceList(payload)

        assertEquals(1, devices.size)
        assertEquals(DeviceState.UNAUTHORIZED, devices[0].state)
    }

    @Test
    fun `parse empty device list`() {
        val devices = parseDeviceList(ByteArray(0))
        assertEquals(0, devices.size)
    }

    @Test
    fun `parse device list with blank lines`() {
        val payload = "\n\nemulator-5554\tdevice\n\n".toByteArray()
        val devices = parseDeviceList(payload)

        assertEquals(1, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
    }

    @Test
    fun `parse forward list`() {
        val payload = "emulator-5554 tcp:9000 tcp:8080\n".toByteArray()
        val forwards = parseForwardList(payload)

        assertEquals(1, forwards.size)
        assertEquals("emulator-5554", forwards[0].serial)
        assertEquals("tcp:9000", forwards[0].local)
        assertEquals("tcp:8080", forwards[0].remote)
    }

    @Test
    fun `parse forward list empty`() {
        val forwards = parseForwardList(ByteArray(0))
        assertEquals(0, forwards.size)
    }

    @Test
    fun `host transport request format`() {
        val serial = "emulator-5554"
        val service = "host:transport:$serial"  // 16 + 13 = 28 chars → 0x1c
        val request = "%04x$service".format(service.length)

        assertEquals("001chost:transport:emulator-5554", request)
    }

    @Test
    fun `smart socket length padding`() {
        val short = "ab"
        assertEquals("0002", "%04x".format(short.length))

        val long = "x".repeat(127)
        assertEquals("007f", "%04x".format(long.length))
    }

    private fun parseDeviceList(payload: ByteArray): List<AdbDevice> {
        val text = String(payload, Charsets.UTF_8).trim()
        if (text.isEmpty()) return emptyList()

        return text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split("\t")
            if (parts.size < 2) return@mapNotNull null
            AdbDevice(serial = parts[0], state = DeviceState.fromString(parts[1]))
        }
    }

    private fun parseForwardList(payload: ByteArray): List<com.young.sample.adblib.model.ForwardEntry> {
        val text = String(payload, Charsets.UTF_8).trim()
        if (text.isEmpty()) return emptyList()

        return text.lines().mapNotNull { line ->
            val parts = line.trim().split(" ")
            if (parts.size < 3) return@mapNotNull null
            com.young.sample.adblib.model.ForwardEntry(
                serial = parts[0], local = parts[1], remote = parts[2]
            )
        }
    }
}
