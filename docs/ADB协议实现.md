# ADB 协议实现

> **权威参考**: 本设计基于 AOSP 官方协议文档，详见同目录下的 AOSP 原始文档副本：
> - [AOSP-protocol.md](AOSP-protocol.md) — 传输协议规范
> - [AOSP-SERVICES.md](AOSP-SERVICES.md) — 服务协议规范
> - [AOSP-SYNC.md](AOSP-SYNC.md) — 文件同步协议规范
> - [AOSP-OVERVIEW.md](AOSP-OVERVIEW.md) — 架构概览
>
> 官方原始文件位于：https://android.googlesource.com/platform/packages/modules/adb/

## 协议层次

ADB 协议分为**三层**：

1. **Smart Socket 协议**（Client ↔ ADB Server）：4字节 hex 长度前缀 + 文本命令，运行在 TCP 5037
2. **传输协议（Wire Protocol）**（Server ↔ adbd）：24字节定长消息头 + 可变长 payload
3. **服务协议**（在 OPEN 流之上）：Shell v2 / Sync 二进制协议 / Framebuffer 等

## 一、传输协议 — 24字节消息头

### 结构定义 (`amessage`)

来自 AOSP `protocol.txt`，字段名使用官方命名：

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

```
偏移  0        4        8        12       16       20
     ┌────────┬────────┬────────┬────────┬────────┬────────┐
     │command │  arg0  │  arg1  │length  │crc32   │ magic  │
     │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │ 4B LE  │
     └────────┴────────┴────────┴────────┴────────┴────────┘
                                           magic = command ^ 0xFFFFFFFF
```

总长度：**24 + data_length** 字节

### 命令常量

4个 ASCII 字符打包为 little-endian int32（AOSP 使用 `A_` 前缀）：

| 命令 (AOSP) | ASCII | Hex | arg0 | arg1 | 说明 |
|-------------|-------|-----|------|------|------|
| A_SYNC | "SYNC" | `0x434e5953` | — | — | 内部桥接，不通过线路发送 |
| A_CNXN | "CNXN" | `0x4e584e43` | version `0x01000001` | maxdata (1MB) | 连接请求/版本协商 |
| A_AUTH | "AUTH" | `0x48545541` | type (1/2/3) | 0 | 认证握手 |
| A_OPEN | "OPEN" | `0x4e45504f` | local_id | 0 | 打开流到服务 |
| A_OKAY | "OKAY" | `0x59414b4f` | local_id | remote_id | 流就绪，允许写入 |
| A_CLSE | "CLSE" | `0x45534c43` | local_id | remote_id | 关闭流 |
| A_WRTE | "WRTE" | `0x45545257` | local_id | remote_id | 写数据 |

### 关键协议规则

**单次未完成 WRITE 规则**（AOSP protocol.txt 原文）：
> Only one A_WRTE can be outstanding at a time; the sender must wait for A_OKAY before sending another A_WRTE. This is a critical flow-control mechanism.

**CRC32 跳过**：自 2017 年 12 月起（version 0x01000001），`data_crc32` 通常设为 0 且不做强制校验。

**A_OKAY 语义**：A_OKAY 表示"流就绪可写"（READY），不仅仅是确认——必须收到对方的 A_OKAY 才能发送 A_WRTE。

### 认证类型 (A_AUTH arg0)

| 值 | 常量 | 说明 |
|----|------|------|
| 1 | TOKEN | 服务端发送随机 token，要求客户端签名 |
| 2 | SIGNATURE | 客户端返回签名后的 token |
| 3 | RSAPUBLICKEY | 客户端发送 RSA 公钥 |

### 实现要点

```kotlin
// AdbMessage.kt — 封装 24 字节 header 的解析和构造
object AdbMessage {
    const val SIZE = 24
    fun create(command: Int, arg0: Int, arg1: Int, payload: ByteArray): ByteArray
    fun parse(bytes: ByteArray): ParsedMessage
}

data class ParsedMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val dataLength: Int,
    val dataCrc32: Int,   // 官方命名为 data_crc32
    val magic: Int
)

// AdbPacket.kt — 完整的 header + payload
data class AdbPacket(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payload: ByteArray
)
```

`data_crc32` 为 payload 的 CRC32 校验和。自 version 0x01000001 起可设为 0 跳过校验。
`magic` 为 `command xor 0xFFFFFFFF`，用于错误检测。
验证公式：`(msg.magic xor msg.command) == 0xFFFFFFFF.toInt()`

## 二、连接与认证流程

### 完整握手序列

```
Client (App)                              ADB Server (host:5037)
     │                                              │
  1. │── TCP connect ──────────────────────────────▶│
     │                                              │
  2. │── A_CNXN(version=0x01000001,                 │
     │      maxdata=1024*1024,                       │
     │      payload="host::\0") ───────────────────▶│
     │                                              │
  3. │                                              │── 判断是否需要认证
     │◀── A_CNXN(version, maxdata, "device::") ────│  (无需认证：直接完成)
     │   或                                          │
     │◀── A_AUTH(TOKEN, 20字节随机数) ─────────────│  (需要认证)
     │── A_AUTH(SIGNATURE, RSA签名结果) ──────────▶│
     │◀── A_CNXN(version, maxdata, "device::") ────│  (认证通过)
```

### 认证细节

1. 客户端收到 `AUTH(TOKEN)` 后，用 RSA 私钥对 token 进行 SHA-1 签名
2. 签名结果作为 payload 发出 `AUTH(SIGNATURE)`
3. 如果签名不被接受（如首次连接），服务端会再次发送 TOKEN
4. 此时客户端应发送 `AUTH(RSAPUBLICKEY)` 带上公钥，让用户可以在设备上授权
5. 然后重新接收 TOKEN，签名后发送

### RSA 密钥对

- 2048-bit RSA 密钥对
- 首次生成后持久化到文件（`adb_lib_key`），后续复用
- 公钥编码为 ADB 特定格式（以 `"<key_data>\0<name>@<host>\0"` 结尾的 Base64）

## 三、Host Service 协议（与 ADB Server 通信）

### 文本协议格式

```
请求:  <4字节hex长度><命令字符串>
响应:  OKAY (成功)
       FAIL<4字节hex长度><错误信息> (失败)
       或: <4字节hex值> (host:version 专用)
```

### 支持的 Host Service

| 服务字符串 | 响应 | 说明 |
|-----------|------|------|
| `host:version` | 4字节hex | 获取 ADB Server 版本 |
| `host:devices` | 设备列表文本 | 列出已连接设备，格式: `serial\tstate\n` |
| `host:devices-l` | 设备详情文本 | 列出设备及属性 |
| `host:track-devices` | 持续推送 | 长连接，设备变化时推送 |
| `host:kill` | - | 终止 ADB Server |
| `host:transport:<serial>` | OKAY/FAIL | 切换到指定设备 |
| `host:transport-any` | OKAY/FAIL | 切换到任一设备 |
| `host:forward:<cmd>` | OKAY/FAIL | 端口转发管理 |
| `host:reverse:<cmd>` | OKAY/FAIL | 反向端口转发 |

### 示例交互

```
→ 0012host:version
← 0029               (0x29 = 41 → ADB Server 版本 41)

→ 000Chost:devices
← OKAYemulator-5554\tdevice\n

→ 001Ahost:transport:emulator-5554
← OKAY               (后续命令路由到该设备)
```

## 四、Shell 服务协议 v2

当设备支持 `kFeatureShell2` 时使用此协议。基于单字节类型 ID 的多路复用分包。

### 包格式

```
┌────────┬───────────┬──────────┐
│ 1B: id │ varint:len│ payload  │
└────────┴───────────┴──────────┘
```

### ID 类型

| ID | 常量 | 方向 | 说明 |
|----|------|------|------|
| 0 | stdin | App→Device | 标准输入数据 |
| 1 | stdout | Device→App | 标准输出数据 |
| 2 | stderr | Device→App | 标准错误输出 |
| 3 | exit | Device→App | 退出码（process 结束） |

### 实现

```kotlin
class ShellProtocol(private val transport: Transport) {
    // 从流的 WRTE 包中解析 Shell v2 子包
    fun parsePacket(data: ByteArray): ShellData?
    
    // 构造 stdin 子包
    fun buildStdin(data: ByteArray): ByteArray
}

sealed class ShellData {
    data class Stdout(val data: ByteArray) : ShellData()
    data class Stderr(val data: ByteArray) : ShellData()
    data class Exit(val code: Int) : ShellData()
}
```

## 五、Sync 服务协议（文件传输）

打开 `sync:` 服务后使用的二进制协议。详见 [AOSP-SYNC.md](AOSP-SYNC.md)。

### 包格式 — 8字节 Header

```
┌─────────────────┬──────────────┐
│ 4B: ID (ASCII)  │ 4B: length   │
│                 │   (LE int32) │
└─────────────────┴──────────────┘
```

所有整数为 Little-Endian。最大数据块 **≤ 64KB**。

### 命令与响应

#### Client → Server（请求）

| ID | ASCII | 说明 |
|----|-------|------|
| LIST | `"LIST"` | 列出目录 |
| RECV | `"RECV"` | 拉取文件 |
| SEND | `"SEND"` | 推送文件 |
| STAT | `"STAT"` | 获取文件信息 |
| QUIT | `"QUIT"` | 退出 sync 模式 |

所有请求后跟 `length` 字节的 UTF-8 路径字符串。
SEND 的路径格式为 `<path>,<mode>`（mode 为十进制权限）。

#### Server → Client（响应）

| ID | ASCII | 说明 |
|----|-------|------|
| DATA | `"DATA"` | 文件数据块（≤64KB） |
| DENT | `"DENT"` | 目录条目（20B + name） |
| DONE | `"DONE"` | 传输/列表完成 |
| OKAY | `"OKAY"` | 成功确认 |
| FAIL | `"FAIL"` | 错误 |

### DENT 条目结构

```
ID(4B) │ mode(4B LE) │ size(4B LE) │ mtime(4B LE) │ namelen(4B LE) │ name(namelen B)
```

### 推送流程 (SEND)
```
Client: SEND<path_len><path>,<mode>
Client: DATA<chunk_size><chunk_data>
Client: DATA<chunk_size><chunk_data>   // 每块 ≤64KB
        ...
Client: DONE<timestamp>
Server: OKAY<0>  (or FAIL<len><msg>)
```
> 注意：Server 只在最后的 DONE 后响应，不逐个确认 DATA 块

### 拉取流程 (RECV)
```
Client: RECV<path_len><path>
Server: DATA<chunk_size><chunk_data>   // 每块 ≤64KB
        ...
Server: DONE<0>
```

## 六、Framebuffer 服务

打开 `framebuffer:` 服务后，Server 发送 16 字节 header（来自 AOSP SERVICES.TXT）：

```
[depth:  uint32_t LE]   // 深度 (当前实现恒为 16)
[size:   uint32_t LE]   // 帧缓冲区字节数 (= width * height * 2)
[width:  uint32_t LE]   // 像素宽度
[height: uint32_t LE]   // 像素高度
```

每次客户端发送 **1 字节**请求，服务端返回 `size` 字节的原始帧数据。需要 root 权限。
