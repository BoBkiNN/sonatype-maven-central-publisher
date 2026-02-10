import org.junit.jupiter.api.Assertions
import xyz.bobkinn.sonatypepublisher.utils.HashUtils
import java.io.File
import kotlin.test.Test

class TestAlgorithms {

    @Test
    fun testCreated() {
        val file = File(javaClass.classLoader.getResource("example.txt")!!.toURI())
        val res = HashUtils.writesFilesHashes(file.parentFile, listOf("MD5", "SHA-1"))
        Assertions.assertEquals(2, res.size)
        res.forEach {
            println(it)
            Assertions.assertTrue(it.exists())
        }
    }
}