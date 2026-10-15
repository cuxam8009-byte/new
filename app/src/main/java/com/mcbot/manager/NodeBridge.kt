package com.mcbot.manager

/** Cầu nối JNI tới libnode.so (Node.js chạy ngay trong app). */
object NodeBridge {
    init {
        System.loadLibrary("node")
        System.loadLibrary("nodebridge")
    }

    /** Chạy Node với argv; hàm này chặn cho tới khi Node kết thúc. */
    external fun startNode(args: Array<String>): Int
}
