package com.onevoice.nativeapp

object ScriptSplitter {
    fun sections(raw: String, maxChars: Int = 230): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val paragraphs = raw.replace("\r\n", "\n").replace('\r','\n')
            .split('\n').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
        for (paragraph in paragraphs) {
            val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
            var current = ""
            fun emit(chunk: String, pause: Int = 150) {
                if (chunk.isNotBlank()) result.add(Pair(chunk.trim(), pause))
            }
            for (sentence in sentences) {
                if (sentence.length <= maxChars) {
                    if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) {
                        emit(current)
                        current = ""
                    }
                    current = (current + " " + sentence).trim()
                    continue
                }
                emit(current)
                current = ""
                var part = ""
                for (word in sentence.split(Regex("\\s+"))) {
                    var remainder = word
                    while (remainder.length > maxChars) {
                        emit(part)
                        part = ""
                        emit(remainder.substring(0, maxChars))
                        remainder = remainder.substring(maxChars)
                    }
                    if (part.isNotEmpty() && part.length + remainder.length + 1 > maxChars) {
                        emit(part)
                        part = ""
                    }
                    part = (part + " " + remainder).trim()
                }
                emit(part)
            }
            emit(current)
            if (result.isNotEmpty()) {
                val last = result.last()
                result[result.lastIndex] = Pair(last.first, 400)
            }
        }
        if (result.isNotEmpty()) {
            val last = result.last()
            result[result.lastIndex] = Pair(last.first, 0)
        }
        return result
    }
}
