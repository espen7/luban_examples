
import cfg.Tables
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    val tables: Tables = Tables({ file ->
        com.google.gson.JsonParser.parseString(
            try {
                String(
                    Files.readAllBytes(Paths.get("../GenerateDatas/json", file + ".json")),
                    charset("UTF-8")
                )
            } catch (e: Exception) {
                println("Error: ${e.message}")
                throw RuntimeException(e)
            }
        )
    })
    println("== run == " + tables.getTbGlobalConfig().x1)
}