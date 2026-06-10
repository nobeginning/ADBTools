# protocol.txt — ADB 传输协议规范

> 来源：AOSP `platform/packages/modules/adb/protocol.txt`
> URL：https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt

---

The transport layer deals in "messages", which consist of a 24 byte header
followed (optionally) by a payload. The header consists of 6 32 bit words
which are sent in little endian format.

```c
struct message {
    unsigned command;       /* command identifier constant (A_CNXN, ...) */
    unsigned arg0;          /* first argument                            */
    unsigned arg1;          /* second argument                           */
    unsigned data_length;   /* length of payload (0 is allowed)          */
    unsigned data_crc32;    /* crc32 of data payload                     */
    unsigned magic;         /* command ^ 0xffffffff                      */
};
```

## Command Constants

```c
#define A_SYNC  0x434e5953
#define A_CNXN  0x4e584e43
#define A_AUTH  0x48545541
#define A_OPEN  0x4e45504f
#define A_OKAY  0x59414b4f
#define A_CLSE  0x45534c43
#define A_WRTE  0x45545257
```

These are ASCII strings, reversed for little-endian:

| Constant | Hex       | ASCII (Big-Endian) |
|----------|-----------|--------------------|
| A_SYNC   | 0x434e5953 | SYNC               |
| A_CNXN   | 0x4e584e43 | CNXN               |
| A_AUTH   | 0x48545541 | AUTH               |
| A_OPEN   | 0x4e45504f | OPEN               |
| A_OKAY   | 0x59414b4f | OKAY               |
| A_CLSE   | 0x45534c43 | CLSE               |
| A_WRTE   | 0x45545257 | WRTE               |

## Message Types (Full Descriptions)

### CONNECT(version, maxdata, "system-identity-string") — A_CNXN
Establishes the presence of a remote system. Both sides send a CONNECT
message when a connection is established. Until a CONNECT is received,
no other messages may be sent.

Currently version = 0x01000001, maxdata = 1024*1024 (1MB).

System identity string format:
    "<systemtype>:<serialno>:<banner>"
Examples:
    "host::"              (host ADB server)
    "device:XXX123:..."   (device/emulator adbd)

### AUTH(type, 0, "data") — A_AUTH
Authentication message. Types:
- TOKEN (1): Server sends a random token to be signed
- SIGNATURE (2): Client returns the signed token
- RSAPUBLICKEY (3): Client sends its public key for on-device authorization

### OPEN(local-id, 0, "destination") — A_OPEN
Opens a stream to a named destination on the remote side.
Common destinations: "tcp:<host>:<port>", "udp:<host>:<port>",
"local-dgram:", "local-stream:", "shell", "upload", "fs-bridge".

### READY(local-id, remote-id, "") — A_OKAY
Tells the peer that the sender is ready for writes. Both sides must send
a READY (A_OKAY) before any WRITE (A_WRTE) can be sent on the stream.

### WRITE(local-id, remote-id, "data") — A_WRTE
Sends data to the recipient's stream. The payload MUST NOT be larger than
maxdata. A WRITE may not be sent until a READY is received from the peer.
There can only be a single outstanding WRITE; the sender must not send
another WRITE until a READY is received from the peer.

### CLOSE(local-id, remote-id, "") — A_CLSE
Indicates that the connection between the sender's (local-id) and
recipient's (remote-id) streams is broken. remote-id MUST NOT be 0.
If a stream open fails, the sender can send a CLOSE with local-id=0
to indicate the failure.

### SYNC(online, sequence, "") — A_SYNC
Internal to the bridge (io pump), never sent across the wire. Used
to discard stale outbound messages when a remote connection breaks.

## Protocol Versions

```c
#define A_VERSION_MIN            0x01000000  // original
#define A_VERSION_SKIP_CHECKSUM  0x01000001  // skip checksum (Dec 2017)
#define A_VERSION                0x01000001
```

## Max Payload Sizes

```c
constexpr size_t MAX_PAYLOAD_V1 = 4 * 1024;   // 4 KB (legacy)
constexpr size_t MAX_PAYLOAD    = 1024 * 1024; // 1 MB (current)
```

## Header Field Naming Convention

The protocol uses the following field names:

| Offset | Size | Field Name   | Description                                       |
|--------|------|-------------|---------------------------------------------------|
| 0      | 4    | command     | Command identifier constant                       |
| 4      | 4    | arg0        | First argument (e.g., local_id, auth_type)       |
| 8      | 4    | arg1        | Second argument (e.g., remote_id, 0)             |
| 12     | 4    | data_length | Length of payload (0 is allowed)                  |
| 16     | 4    | data_crc32  | CRC32 checksum of payload                        |
| 20     | 4    | magic       | command ^ 0xffffffff (error detection)            |

Total header size: **24 bytes**

## Important Implementation Notes

1. **A_VERSION_SKIP_CHECKSUM**: Since version 0x01000001 (Dec 2017),
   the data_crc32 field is commonly set to 0 and not checked. Modern
   clients should set it to 0 and not reject packets with 0 crc32.

2. **Single Outstanding WRITE**: Only one A_WRTE can be in flight at a
   time. The sender MUST wait for A_OKAY from the peer before sending
   another A_WRTE. This is a critical flow-control mechanism.

3. **Stream Pairing**: When A_OPEN succeeds, the responder sends A_OKAY
   with remote_id = its own local_id for the new stream. This pairs the
   two local IDs into a bidirectional stream.
