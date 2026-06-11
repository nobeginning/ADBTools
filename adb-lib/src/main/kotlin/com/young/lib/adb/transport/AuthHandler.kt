package com.young.lib.adb.transport

import android.util.Log
import com.young.lib.adb.AdbCommand
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.protocol.AdbPacket
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * ADB RSA 认证处理器。
 *
 * 处理 host 端直连 adbd 时的认证握手。
 * 注意: 连接 ADB Server 时不需要认证，认证仅发生在直连 adbd 的 transport 层面。
 *
 * @param keyStoreDir 密钥文件存储目录
 */
class AuthHandler(private val keyStoreDir: File) {

    companion object {
        private const val TAG = "ADB-Auth"
        private const val KEY_FILE_NAME = "adb_lib_key"
        private const val KEY_FILE_VERSION = "v3"  // 签名方法改用 RSA/ECB/NoPadding + divideAndRemainder 字提取
        private const val MAX_AUTH_RETRIES = 5
        private const val KEY_SIZE_BYTES = 256  // RSA 2048
        private const val KEY_SIZE_WORDS = KEY_SIZE_BYTES / 4  // 64

        /** PKCS#1 v1.5 签名填充前缀（DER 编码的 SHA-1 DigestInfo）。
         *  填充后紧跟 20 字节的 TOKEN 数据，总长度 = 模数长度 (256 bytes)。
         *  参见 magpie-android/AdbLib AdbCrypto.java 的 SIGNATURE_PADDING。 */
        private val SIGNATURE_PADDING: ByteArray by lazy {
            val ints = intArrayOf(
                0x00,0x01,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,
                0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0x00,
                0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,
                0x04,0x14
            )
            ByteArray(ints.size) { ints[it].toByte() }
        }

        private fun commandName(cmd: Int): String = when (cmd) {
            AdbCommand.CNXN -> "CNXN"
            AdbCommand.AUTH -> "AUTH"
            AdbCommand.OPEN -> "OPEN"
            AdbCommand.OKAY -> "OKAY"
            AdbCommand.CLSE -> "CLSE"
            AdbCommand.WRTE -> "WRTE"
            else -> "0x${cmd.toUInt().toString(16)}"
        }

        private fun authTypeName(type: Int): String = when (type) {
            AdbCommand.AUTH_TOKEN -> "TOKEN"
            AdbCommand.AUTH_SIGNATURE -> "SIGNATURE"
            AdbCommand.AUTH_RSA_PUBLIC -> "RSAPUBLICKEY"
            else -> "未知($type)"
        }

        /** 将 ByteArray 转换为十六进制字符串（用于调试） */
        private fun hexDump(bytes: ByteArray, maxLen: Int = 64): String {
            val len = minOf(bytes.size, maxLen)
            val hex = StringBuilder()
            for (i in 0 until len) {
                hex.append(String.format("%02x", bytes[i]))
                if ((i + 1) % 4 == 0 && i + 1 < len) hex.append(' ')
            }
            if (bytes.size > maxLen) hex.append("...(${bytes.size} bytes total)")
            return hex.toString()
        }
    }

    /** RSA 密钥对，懒加载（首次使用时从文件加载或生成） */
    private val keyPair: KeyPair by lazy { loadOrGenerateKeyPair() }

    private val keyFile: File get() = File(keyStoreDir, KEY_FILE_NAME)

    /**
     * 认证主流程。
     *
     * 客户端先发 CNXN，然后调用此方法处理对端响应。
     * 对端回复 CNXN → 无需认证；回复 AUTH → 走认证握手。
     */
    suspend fun authenticate(transport: Transport): Boolean {
        val response = transport.read()
        Log.d(TAG, "收到对端响应: ${commandName(response.command)}, arg0=${response.arg0}, arg1=${response.arg1}, payloadLen=${response.dataLength}")
        if (response.payload.isNotEmpty()) {
            Log.d(TAG, "  响应 payload 前64字节: ${hexDump(response.payload)}")
        }

        return when (response.command) {
            AdbCommand.CNXN -> {
                transport.negotiateMaxPayload(response.arg1)
                Log.i(TAG, "认证跳过 — 对端直接返回 CNXN（密钥已授权），协商 maxdata=${response.arg1}")
                true
            }
            AdbCommand.AUTH -> {
                Log.i(TAG, "需要认证握手，对端请求类型: ${authTypeName(response.arg0)}")
                handleAuthFlow(transport, response)
            }
            else -> {
                Log.e(TAG, "预期 CNXN 或 AUTH，实际收到: ${commandName(response.command)}")
                throw AdbException.AuthenticationFailed(
                    "Expected CNXN or AUTH, got command=0x${response.command.toUInt().toString(16)}"
                )
            }
        }
    }

    /**
     * 认证握手流程。
     *
     * 参照 AOSP 协议文档 (docs/ADB协议实现.md):
     *   首次连接:
     *     TOKEN → SIGNATURE → (被拒,收到TOKEN) → RSAPUBLICKEY → (收到TOKEN) → SIGNATURE → CNXN
     *   已授权连接:
     *     TOKEN → SIGNATURE → CNXN
     *
     * 关键: SIGNATURE 被拒（对端再次发 TOKEN 而非 RSAPUBLICKEY）时，
     * 应发送 RSAPUBLICKEY 让用户在设备上授权，而不是无限循环签名。
     */
    private suspend fun handleAuthFlow(transport: Transport, firstPacket: AdbPacket): Boolean {
        var packet = firstPacket
        var attemptCount = 0
        var keySent = false
        // 标记: 上一步是否刚发送了 SIGNATURE（用于判断 TOKEN 是"新请求"还是"拒绝"）
        var signatureJustSent = false

        while (attemptCount < MAX_AUTH_RETRIES) {
            attemptCount++
            Log.d(TAG, "--- 认证第 $attemptCount/$MAX_AUTH_RETRIES 轮 (keySent=$keySent, signatureJustSent=$signatureJustSent) ---")

            when (packet.arg0) {
                AdbCommand.AUTH_TOKEN -> {
                    val token = packet.payload
                    if (token.isEmpty()) {
                        Log.e(TAG, "收到空 TOKEN")
                        throw AdbException.AuthenticationFailed("Empty auth token")
                    }

                    // 关键判断: 如果刚发送了 SIGNATURE 又收到 TOKEN，说明签名被拒
                    // 此时应发送 RSAPUBLICKEY（公钥），而不是再次签名
                    if (signatureJustSent && !keySent) {
                        Log.w(TAG, "⚠ SIGNATURE 被 adbd 拒绝（收到新的 TOKEN），发送 RSAPUBLICKEY 请求用户授权...")
                        val publicKeyPayload = buildPublicKeyPayload()
                        Log.i(TAG, "发送公钥（总长度=${publicKeyPayload.size}），请观察手机屏幕是否弹出授权对话框...")
                        transport.write(AdbPacket.auth(AdbCommand.AUTH_RSA_PUBLIC, publicKeyPayload))
                        keySent = true
                        signatureJustSent = false
                        Log.i(TAG, "公钥已发送 — 请在手机上点击「允许 USB 调试」并勾选「一律允许」")
                    } else {
                        Log.d(TAG, "收到 TOKEN（长度=${token.size}），准备签名...")
                        Log.d(TAG, "  TOKEN hex: ${hexDump(token)}")
                        val signature = signToken(token)
                        // 自验证：确认签名可用原始公钥验证通过
                        val selfCheck = selfVerify(token, signature)
                        Log.d(TAG, "签名自验证: ${if (selfCheck) "✅ 通过" else "❌ 失败！密钥对不匹配！"}")
                        Log.d(TAG, "发送 SIGNATURE（签名长度=${signature.size}）")
                        transport.write(AdbPacket.auth(AdbCommand.AUTH_SIGNATURE, signature))
                        signatureJustSent = true
                        Log.d(TAG, "SIGNATURE 已发送")
                    }
                }
                AdbCommand.AUTH_SIGNATURE -> {
                    Log.w(TAG, "收到 SIGNATURE 类型（极少见），表示上次签名被拒，忽略并等待下一包")
                    signatureJustSent = false
                }
                AdbCommand.AUTH_RSA_PUBLIC -> {
                    Log.i(TAG, "收到 RSAPUBLICKEY 请求 — adbd 显式要求公钥...")
                    val publicKeyPayload = buildPublicKeyPayload()
                    Log.i(TAG, "发送公钥（总长度=${publicKeyPayload.size}），等待用户授权...")
                    transport.write(AdbPacket.auth(AdbCommand.AUTH_RSA_PUBLIC, publicKeyPayload))
                    keySent = true
                    signatureJustSent = false
                    Log.i(TAG, "公钥已发送 — 请观察手机屏幕是否弹出「允许 USB 调试」对话框")
                }
                else -> {
                    Log.e(TAG, "未知认证类型: arg0=${packet.arg0}")
                    throw AdbException.AuthenticationFailed("Unknown auth type: ${packet.arg0}")
                }
            }

            // 读取响应
            Log.d(TAG, "等待对端响应...")
            val response = transport.read()
            Log.d(TAG, "收到响应: ${commandName(response.command)}, arg0=${response.arg0}, arg1=${response.arg1}, payloadLen=${response.dataLength}")
            if (response.payload.isNotEmpty()) {
                Log.d(TAG, "  响应 payload 前64字节: ${hexDump(response.payload)}")
            }

            when (response.command) {
                AdbCommand.CNXN -> {
                    transport.negotiateMaxPayload(response.arg1)
                    Log.i(TAG, "✅ 认证成功！对端返回 CNXN，协商 maxdata=${response.arg1}")
                    return true
                }
                AdbCommand.AUTH -> {
                    Log.d(TAG, "继续认证，对端请求: ${authTypeName(response.arg0)}")
                    // 如果收到的是 RSAPUBLICKEY，说明 adbd 在收到 SIGNATURE 后显式请求公钥
                    // 此时清空 signatureJustSent 标记，下一轮会走 RSAPUBLICKEY 分支
                    if (response.arg0 == AdbCommand.AUTH_RSA_PUBLIC) {
                        Log.i(TAG, "adbd 请求公钥 — 将在下一轮发送")
                    }
                    packet = response
                }
                else -> {
                    Log.e(TAG, "认证过程中收到意外响应: ${commandName(response.command)}")
                    throw AdbException.AuthenticationFailed(
                        "Unexpected response during auth: 0x${response.command.toUInt().toString(16)}"
                    )
                }
            }
        }

        Log.e(TAG, "❌ 认证失败: $MAX_AUTH_RETRIES 轮握手后仍未收到 CNXN")
        if (!keySent) {
            Log.e(TAG, "💡 提示: 公钥从未发送，adbd 拒绝了所有 SIGNATURE 但未请求 RSAPUBLICKEY")
        }
        throw AdbException.AuthenticationFailed(
            "Authentication failed after $MAX_AUTH_RETRIES attempts"
        )
    }

    /**
     * 使用 RSA 私钥对 TOKEN 进行签名。
     *
     * ⚠ ADB 认证协议要求直接对原始 20 字节 TOKEN 签名（而非 SHA1(TOKEN)）。
     * TOKEN 本身长度为 20 字节（= SHA-1 哈希长度），恰好能填入 PKCS#1 v1.5 的哈希槽。
     * 参见 magpie-android/AdbLib AdbCrypto.java 的 signAdbTokenPayload 方法。
     *
     * @param token ADB AUTH TOKEN 的原始 20 字节 payload
     * @return 256 字节 RSA 签名
     */
    fun signToken(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        cipher.update(SIGNATURE_PADDING)  // 手动 PKCS#1 v1.5 前缀（含 SHA-1 DigestInfo）
        return cipher.doFinal(token)       // 原始 TOKEN 直接填入哈希槽
    }

    /** 自验证：用公钥解密签名，检查最后 20 字节是否等于原始 token */
    fun selfVerify(token: ByteArray, signature: ByteArray): Boolean {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyPair.public)
            val decrypted = cipher.doFinal(signature)
            val offset = decrypted.size - token.size
            decrypted.copyOfRange(offset, decrypted.size).contentEquals(token)
        } catch (e: Exception) {
            Log.e(TAG, "自验证异常: ${e.message}")
            false
        }
    }

    /**
     * 构建 ADB 协议格式的公钥 payload。
     *
     * 使用 BigInteger.divideAndRemainder(2^32) 提取 LE 32-bit 字数组，
     * 与 magpie-android/AdbLib AdbCrypto.java 的 convertRsaPublicKeyToAdbFormat 方法一致。
     *
     * payload 格式: base64(mincrypt_struct) unknown@unknown\0
     */
    fun buildPublicKeyPayload(): ByteArray {
        val pub = keyPair.public as RSAPublicKey
        val modulus = pub.modulus
        val exponent = pub.publicExponent

        val r32 = BigInteger.ONE.shiftLeft(32)  // 2^32
        val r = BigInteger.ONE.shiftLeft(KEY_SIZE_WORDS * 32)  // 2^(64*32)
        var n = modulus
        var rr = r.modPow(BigInteger.valueOf(2), modulus)  // R^2 mod n

        // n0inv = -n[0]^(-1) mod 2^32
        val n0 = n.remainder(r32)
        val n0inv = n0.modInverse(r32).negate().toInt()

        val myN = IntArray(KEY_SIZE_WORDS)   // modulus 的 LE 字数组
        val myRr = IntArray(KEY_SIZE_WORDS)  // R^2 mod n 的 LE 字数组

        for (i in 0 until KEY_SIZE_WORDS) {
            // 逐字提取 LSW（除以 2^32，余数为当前 LSW）
            val rrDivRem = rr.divideAndRemainder(r32)
            rr = rrDivRem[0]
            myRr[i] = rrDivRem[1].toInt()

            val nDivRem = n.divideAndRemainder(r32)
            n = nDivRem[0]
            myN[i] = nDivRem[1].toInt()
        }

        // 用 LE ByteBuffer 组装 mincrypt RSAPublicKey 结构
        val buf = ByteBuffer.allocate(4 + 4 + KEY_SIZE_WORDS * 4 + KEY_SIZE_WORDS * 4 + 4)
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(KEY_SIZE_WORDS)
        buf.putInt(n0inv)
        for (word in myN) buf.putInt(word)
        for (word in myRr) buf.putInt(word)
        buf.putInt(exponent.toInt())

        // Base64 + 空格分隔的身份 + NUL 终止
        val base64Key = Base64.getEncoder().encodeToString(buf.array())
        val identity = "adbtools@localhost"
        val payload = ("$base64Key $identity ").toByteArray()

        Log.i(TAG, "构建公钥 payload: nwords=$KEY_SIZE_WORDS, " +
                "n0inv=0x${n0inv.toUInt().toString(16)}, exponent=$exponent, " +
                "base64长度=${base64Key.length}, 身份=$identity, 总长度=${payload.size}")

        return payload
    }

    // ---- 密钥管理 ----

    private fun loadOrGenerateKeyPair(): KeyPair {
        if (keyFile.exists()) {
            val content = keyFile.readText().trim()
            // v2 版本标记：确保密钥文件是由修复后的 n0inv 算法生成的
            if (content.startsWith(KEY_FILE_VERSION)) {
                Log.d(TAG, "加载已有密钥 (v2): ${keyFile.absolutePath}")
                return loadKeyPair(content)
            }
            // 旧版本密钥（无版本标记或 v1），删除并重新生成
            Log.w(TAG, "检测到旧版本密钥文件，删除并重新生成: ${keyFile.absolutePath}")
            keyFile.delete()
        }
        Log.i(TAG, "生成新 RSA 2048 密钥对 → ${keyFile.absolutePath}")
        return generateAndSaveKeyPair(keyFile)
    }

    private fun loadKeyPair(content: String): KeyPair {
        // 格式: KEY_FILE_VERSION\n<base64_private>\n---\n<base64_public>
        val payload = content.removePrefix("$KEY_FILE_VERSION\n")
        val parts = payload.split("\n---\n")
        if (parts.size < 2) {
            Log.e(TAG, "密钥文件格式无效: ${keyFile.absolutePath}")
            throw AdbException.AuthenticationFailed("Invalid key file format: ${keyFile.absolutePath}")
        }

        val privateKeyBytes = Base64.getDecoder().decode(parts[0])
        val publicKeyBytes = Base64.getDecoder().decode(parts[1])

        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        Log.d(TAG, "密钥加载成功 (RSA ${(privateKey as java.security.interfaces.RSAKey).modulus.bitLength()} bit)")
        return KeyPair(publicKey, privateKey)
    }

    private fun generateAndSaveKeyPair(file: File): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val pair = keyGen.generateKeyPair()

        file.parentFile?.mkdirs()
        val privateKeyBase64 = Base64.getEncoder().encodeToString(pair.private.encoded)
        val publicKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        file.writeText("$KEY_FILE_VERSION\n$privateKeyBase64\n---\n$publicKeyBase64")

        file.setReadable(true, true)
        file.setWritable(true, true)

        Log.i(TAG, "RSA 2048 密钥对已生成并保存: ${file.absolutePath}")
        return pair
    }

    private fun getHostname(): String {
        // 已不再使用，identity 固定为 "adbtools@localhost"，保留以备将来扩展
        return try {
            java.net.InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "unknown"
        }
    }
}
