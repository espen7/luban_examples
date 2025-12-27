

import cfg.Tables
import luban.ByteBuf
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@Throws(IOException::class)
fun main() {
    val tables = Tables({ obj: String -> createByteBufFromFile(obj) })
    println("== load succ ==")
}

@Throws(IOException::class)
private fun createByteBufFromFile(file: String?): ByteBuf {
    return ByteBuf(Files.readAllBytes(Paths.get("../GenerateDatas/bytes", file + ".bytes")))
}