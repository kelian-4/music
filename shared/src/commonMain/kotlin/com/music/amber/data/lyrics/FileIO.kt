package com.music.amber.data.lyrics

internal expect fun readFileHead(path: String, maxBytes: Int): ByteArray?

internal expect fun writeUtf8File(path: String, text: String): Boolean
