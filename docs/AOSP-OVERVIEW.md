# OVERVIEW.TXT — ADB 架构概览

> 来源：AOSP `platform/packages/modules/adb/OVERVIEW.TXT`
> URL：https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/OVERVIEW.TXT

---

This document describes how the Android Debug Bridge (ADB) works internally.

ADB is made of three basic components:

1.  A **client**, which runs on your development machine.  You can invoke a
    client by issuing an 'adb' command.  The client is essentially a
    command-line application like any other, that happens to connect to an
    ADB server.  Other tools like DDMS or the ADT plugin also create ADB
    clients.

2.  A **server**, which runs as a background process on your development
    machine.  The server manages all communication between the client and
    the adb daemon running on the device or emulator.

3.  A **daemon (adbd)**, which runs as a background process on each device
    or emulator.  The daemon is started when the system boots, and manages
    the communication between the host and the device/emulator.

The client and the server are generally two parts of the same application.
The ADB executable binary on the host contains both a client and a server.
When you run adb, it first checks if there is an ADB server already running.
If there isn't, it automatically starts one.

Architecture diagram:

```
+----------+              +------------------------+
|   ADB    +----------+   |      ADB SERVER        |                   +----------+
|  CLIENT  |          |   |                        |              (USB)|   ADBD   |
+----------+          |   |                     Transport+-------------+ (DEVICE) |
                      |   |                        |                   +----------+
+-----------          |   |                        |
|   ADB    |          v   +                        |                   +----------+
|  CLIENT  +--------->SmartSocket                  |              (USB)|   ADBD   |
+----------+          ^   | (TCP/IP)            Transport+-------------+ (DEVICE) |
                      |   |                        |                   +----------+
+----------+          |   |                        |
|  DDMLIB  |          |   |                     Transport+--+          +----------+
|  CLIENT  +----------+   |                        |        |  (TCP/IP)|   ADBD   |
+----------+              +------------------------+        +----------|(EMULATOR)|
                                                                       +----------+
```

The ADB server manages several "transports" (USB devices or TCP emulators).
Once a client connects to the server's Smart Socket, it can issue commands
that are routed to the appropriate transport for a given device or emulator.

## Communication Flow

1. Client connects to the ADB server on TCP port 5037
2. Client sends a host service request (e.g., "host:devices")
3. To talk to a specific device, client first sends "host:transport:<serial>"
4. After the server responds OKAY, all subsequent data is forwarded directly
   to the device's adbd daemon
5. The client can then send service requests like "shell:ls -la" or "sync:"

## Key Design Points

- The Smart Socket is the core communication channel: clients connect to it,
  send ASCII-based service requests, and receive responses
- Once a transport is selected (host:transport:<serial>), all further
  communication is transparently forwarded to the device
- Each device connection (USB or TCP) is a separate transport
- The server multiplexes multiple client connections to multiple devices
- The ADB protocol (24-byte messages) is used between the server and adbd;
  client-to-server communication uses the Smart Socket protocol (ASCII)
