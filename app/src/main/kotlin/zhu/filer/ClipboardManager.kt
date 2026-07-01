package zhu.filer

import java.io.File

class ClipboardManager {
    private var files: List<File> = emptyList()
    private var action: String? = null

    fun hasContent(): Boolean = files.isNotEmpty() && action != null
    fun getFiles(): List<File> = files
    fun isCut(): Boolean = action == "cut"
    fun set(files: List<File>, isCut: Boolean) {
        this.files = files
        this.action = if (isCut) "cut" else "copy"
    }
    fun set(file: File, isCut: Boolean) {
        set(listOf(file), isCut)
    }
    fun clear() {
        files = emptyList()
        action = null
    }
}