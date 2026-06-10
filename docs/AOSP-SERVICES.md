# SERVICES.TXT — ADB 服务协议规范

> 来源：AOSP `platform/packages/modules/adb/SERVICES.TXT`
> URL：https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/SERVICES.TXT

---

This file documents all requests a client can make to the ADB server
of an adbd daemon. See OVERVIEW.TXT to understand what's going on here.

## HOST SERVICES:

These are commands sent to the ADB server running on the host machine.

### host:version
Ask the ADB server for its internal version number.
**Special**: The server responds with a 4-byte hex string without OKAY/FAIL prefix.

### host:kill
Ask the ADB server to quit immediately.

### host:devices / host:devices-l
Return the list of available Android devices and their state.
After OKAY, followed by a 4-byte hex len and a string of format:
    `<serial>\t<state>\n`
`host:devices-l` includes the device paths in the state.

### host:track-devices
A variant of host:devices which **doesn't close the connection**. Instead,
a new device list description is sent each time a device is added/removed
or the state of a given device changes (hex4 + content). This allows
real-time tracking without polling.

### host:emulator:<port>
Notifies the ADB server when a new emulator starts. <port> is the emulator's
ADB control port (TCP port forwarded to adbd inside the emulator).

### host:transport:<serial-number>
Switch the connection to the device/emulator identified by <serial-number>.
After the OKAY response, every client request will be sent directly to the
adbd daemon running on the device. (Implements the -s option)

### host:transport-usb
Switch to the single USB-connected device. Fails if >1 USB devices.

### host:transport-local
Switch to the single TCP emulator. Fails if >1 emulator.

### host:transport-any
Switch to any available device/emulator. Fails if >1 available.

### host-serial:<serial>:<request>
Target a specific device with a sub-request. Equivalent to:
`host:transport:<serial>` then the `<request>`.

### host-usb:<request> / host-local:<request> / host:<request>
Variants of host-serial targeting USB device, local emulator, or any device.

### <host-prefix>:get-serialno
Returns the serial number of the corresponding device/emulator.

### <host-prefix>:get-devpath
Returns the device path of the corresponding device/emulator.

### <host-prefix>:get-state
Returns the state of a given device as a string.

### <host-prefix>:get-product
XXX (product name query)

### <host-prefix>:forward:<local>;<remote>
Ask the ADB server to forward local connections from <local> to <remote>.

`<local>` format:
- `tcp:<port>` — TCP connection on localhost:<port>
- `local:<path>` — Unix local domain socket on <path>

`<remote>` format:
- `tcp:<port>` — TCP localhost:<port> on device
- `local:<path>` — Unix local domain socket on device
- `jdwp:<pid>` — JDWP thread on VM process <pid>
- Or any local service described below.

### <host-prefix>:forward:norebind:<local>;<remote>
Same as forward but fails if the local endpoint is already bound.
Implements `adb forward --no-rebind`.

### <host-prefix>:killforward:<local>
Remove existing forward from <local>. Implements `adb forward --remove`.

### <host-prefix>:killforward-all
Remove ALL forward connections. Implements `adb forward --remove-all`.

### <host-prefix>:list-forward
List all forward connections. Returns:
```
<hex4>: length of payload
<payload>: "<serial> <local> <remote>\n" lines
```

## LOCAL SERVICES:

These commands are sent to adbd on the device. Must first switch transport
via host:transport:<serial> or use a host prefix.

### shell:command arg1 arg2 ...
Run command in a shell on the device, return stdout/stderr.
Arguments separated by spaces. Spaces inside arguments must be double-quoted.

### shell:
Start an interactive shell session on the device. Redirect stdin/stdout/stderr.

### remount:
Ask adbd to remount the device's filesystem in read-write mode.

### dev:<path>
Open a device file for direct read/write. May require special privileges.

### tcp:<port> / tcp:<port>:<server-name>
Connect to TCP port on localhost or a remote server from the device.

### local:<path> / localreserved:<path> / localabstract:<path> / localfilesystem:<path>
Connect to Unix domain sockets with different namespaces.

### log:<name>
Open a system log (/dev/log/<name>) for reading. Used to implement 'adb logcat'.
Stream is read-only for the client.

### framebuffer:
Send framebuffer snapshots to a client. Requires sufficient privileges.

After OKAY, the service sends a 16-byte binary structure (little-endian):
```
depth:   uint32_t    framebuffer depth
size:    uint32_t    framebuffer size in bytes
width:   uint32_t    framebuffer width in pixels
height:  uint32_t    framebuffer height in pixels
```
(depth is always 16, size is always width*height*2)

Each time the client wants a snapshot, it sends one byte, and the service
responds with 'size' bytes of framebuffer data.

### dns:<server-name>
Runs within the ADB server only. Implements USB networking (gethostbyname).

### recover:<size>
Upload a recovery image to the device. Creates /tmp/update and /tmp/update.start.
Only works in recovery mode.

### jdwp:<pid>
Connect to the JDWP thread of process <pid>.

### track-jdwp
Send the list of JDWP pids periodically to the client. Format:
```
<hex4>: length of all content
<content>: "<pid>\n" lines
```
No single-shot variant — this is continuous only.

### sync:
Start the file synchronization service. Detailed in SYNC.TXT.

### reverse:<forward-command>
Implement 'adb reverse' feature (socket forwarding from device to host).
Sub-commands: list-forward, forward:<local>;<remote>, forward:norebind:...,
killforward-all, killforward:<local>.

Note: <local> is the socket on the DEVICE, <remote> is the socket on the HOST.
(This is the reverse of the host:forward direction.)

reverse:list-forward output is the same as host:list-forward except <serial>
is always 'host'.

---

## Smart Socket Protocol (Client ↔ ADB Server)

All communication between the client and the ADB server uses a simple
text-based protocol over the Smart Socket (TCP port 5037):

```
Request:  <4-byte-hex-length><command-string>
Response: OKAY  (4 bytes, success)
          FAIL<4-byte-hex-length><error-message>  (error)

Exception: host:version returns a 4-byte hex version number directly
```

Example exchange:
```
→ 0012host:version
← 0029                        (version 41 in hex)

→ 000Chost:devices
← OKAY0034emulator-5554\tdevice\n0a1b2c3d\tdevice\n

→ 001Ahost:transport:emulator-5554
← OKAY

→ 000Bshell:ls
← OKAY [binary ADB stream follows...]
```
