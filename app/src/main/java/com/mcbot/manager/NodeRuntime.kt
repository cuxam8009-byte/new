package com.mcbot.manager

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import java.io.File
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.zip.ZipInputStream

/** Giữ trạng thái Node trong process (chỉ khởi động được 1 lần / process). */
object NodeRuntime {
    @Volatile var port = 0
    @Volatile var token = ""
    @Volatile var phase = "Đang chuẩn bị..."
    @Volatile var error: String? = null
    private var started = false

    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        Thread(null, {
            try {
                token = randomToken()
                port = ServerSocket(0).use { it.localPort }

                phase = "Đang giải nén lần đầu (có thể mất vài chục giây)..."
                val dir = install(app)

                phase = "Đang khởi động Node.js..."
                val data = File(app.filesDir, "data").apply { mkdirs() }
                try {
                    Os.setenv("HOME", app.filesDir.absolutePath, true)
                    Os.setenv("TMPDIR", app.cacheDir.absolutePath, true)
                } catch (_: Exception) {
                }
                val code = NodeBridge.startNode(
                    arrayOf("node", File(dir, "main.js").absolutePath, port.toString(), token, data.absolutePath)
                )
                if (error == null) error = "Node đã dừng (mã $code)"
            } catch (t: Throwable) {
                error = t.javaClass.simpleName + ": " + (t.message ?: "")
            }
        }, "node-main", 8L * 1024 * 1024).start()
    }

    private fun randomToken(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Giải nén nodejs-project.zip (trong assets) ra filesDir, chỉ làm lại khi có bản build mới. */
    private fun install(ctx: Context): File {
        val dir = File(ctx.filesDir, "nodejs-project")
        val stamp = File(ctx.filesDir, "nodejs-project.stamp")
        val want = BuildConfig.PROJECT_STAMP
        if (dir.isDirectory && stamp.isFile && stamp.readText() == want && File(dir, "main.js").isFile) return dir

        dir.deleteRecursively()
        dir.mkdirs()
        val root = dir.canonicalPath + File.separator
        ZipInputStream(ctx.assets.open("nodejs-project.zip").buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(dir, e.name)
                if (!out.canonicalPath.startsWith(root)) throw SecurityException("Đường dẫn zip không hợp lệ: ${e.name}")
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        stamp.writeText(want)
        return dir
    }

    /** Thoát hẳn: dừng service rồi kết thúc process (Node không khởi động lại được trong cùng process). */
    fun exitAll(ctx: Context) {
        try {
            ctx.stopService(Intent(ctx, NodeService::class.java))
        } catch (_: Exception) {
        }
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
    }
}
