# SYNC.TXT — ADB 文件同步协议规范

> 来源：AOSP `platform/packages/modules/adb/SYNC.TXT`
> URL：https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/SYNC.TXT

---

The sync service is entered by requesting 'sync:' via the standard ADB
protocol (see SERVICES.TXT).  The server responds with OKAY or FAIL as
usual.  After that, the connection is in **sync mode** — a binary mode
that differs from the regular ADB protocol.  The connection stays in
sync mode until explicitly terminated (via QUIT).

## Packet Format

All sync requests and responses use **8-byte headers**:

| Bytes | Contents                                                          |
|-------|-------------------------------------------------------------------|
| 0-3   | **ID**: 4 ASCII/UTF-8 characters (e.g., `SEND`, `RECV`, `DATA`) |
| 4-7   | **length**: Little-Endian 32-bit integer, usage varies per command|

**All binary integers are Little-Endian.**

## Commands (Client → Server)

| Command | Description                                     |
|---------|-------------------------------------------------|
| LIST    | List files in a directory                       |
| RECV    | Retrieve (pull) a file from device              |
| SEND    | Send (push) a file to device                    |
| STAT    | Stat a file on device                           |
| QUIT    | Exit sync mode (terminate the sync connection)  |

All of the above commands must be followed by `length` bytes containing
a **UTF-8 string** with the remote filename path.

## Responses (Server → Client)

| Response | Description              |
|----------|--------------------------|
| DATA     | File data chunk          |
| DENT     | Directory entry (listing)|
| DONE     | Transfer/listing complete|
| OKAY     | Success acknowledgement  |
| FAIL     | Error                    |

## LIST — Directory Listing Protocol

```
1. Client:   LIST<length><path>
2. Server:   DENT<mode><size><mtime><namelen><name>
             DENT<mode><size><mtime><namelen><name>
             ... (one DENT per entry)
3. Server:   DONE<0>
```

Each **DENT** structure:
| Offset | Size | Field    | Description                          |
|--------|------|----------|--------------------------------------|
| 0      | 4    | ID       | ASCII "DENT"                         |
| 4      | 4    | mode     | File mode (LE int32)                 |
| 8      | 4    | size     | File size in bytes (LE int32)       |
| 12     | 4    | mtime    | Last modified time (LE int32)       |
| 16     | 4    | namelen  | Length of filename (LE int32)       |
| 20     | var. | name     | UTF-8 filename (namelen bytes)      |

**Total DENT header: 20 bytes + filename**

## SEND — Push File to Device

```
1. Client:   SEND<length><path>,<mode>
              (remote path + comma + decimal file mode)
2. Client:   DATA<chunk_size><chunk_data>
             DATA<chunk_size><chunk_data>
             ... (repeat for each chunk)
3. Client:   DONE<timestamp>
4. Server:   OKAY<0> or FAIL<length><error_message>
```

Constraints:
- Remote filename is split at the **last comma**: `<path>,<mode>` (mode is decimal)
- Each DATA chunk MUST be **≤ 64KB**
- The final DONE packet's length field carries the **last modified time** (Unix timestamp)
- Server only responds to the final DONE, NOT to individual DATA chunks

## RECV — Pull File from Device

```
1. Client:   RECV<length><filename>
2. Server:   DATA<chunk_size><chunk_data>
             DATA<chunk_size><chunk_data>
             ... (repeat for each chunk)
3. Server:   DONE<0>
```

Constraints:
- DATA responses carry file contents, chunked at **≤ 64KB** each
- DONE signals end of transfer; its length field can be ignored

## STAT — File Status

```
1. Client:   STAT<length><filename>
2. Server:   STAT<mode><size><mtime>
     or      FAIL<length><reason>
```

STAT response structure:
| Offset | Size | Field | Description                     |
|--------|------|-------|----------------------------------|
| 0      | 4    | ID    | ASCII "STAT"                     |
| 4      | 4    | mode  | File mode (LE int32)            |
| 8      | 4    | size  | File size in bytes (LE int32)  |
| 12     | 4    | mtime | Last modified time (LE int32)  |

## QUIT — Terminate Sync

```
1. Client:   QUIT<0>
2. Connection returns to normal ADB protocol mode
```

After receiving QUIT, the transport is no longer in sync mode.

## Raw Byte Values

```
QUIT = 0x51 0x55 0x49 0x54  <length_LE32>
SEND = 0x53 0x45 0x4e 0x44  <length_LE32>
RECV = 0x52 0x45 0x43 0x56  <length_LE32>
DATA = 0x44 0x41 0x54 0x41  <length_LE32>
DENT = 0x44 0x45 0x4e 0x54  <mode_LE32><size_LE32><mtime_LE32><namelen_LE32>
DONE = 0x44 0x4f 0x4e 0x45  <length_LE32>
OKAY = 0x4f 0x4b 0x41 0x59  <length_LE32>
FAIL = 0x46 0x41 0x49 0x4c  <length_LE32>
STAT = 0x53 0x54 0x41 0x54  <length_LE32>
LIST = 0x4c 0x49 0x53 0x54  <length_LE32>
```
