package eu.kakde.sonatypecentral.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object IOUtils {

    fun renameFile(
        oldFile: File,
        newFileName: String,
    ): File {
        // Check if the old file exists
        if (!oldFile.exists()) {
            throw IllegalArgumentException("File does not exist: ${oldFile.absolutePath}")
        }
        // Extract the parent directory of the old file
        val parentDir = oldFile.parentFile ?: throw IllegalStateException("Parent directory is null")
        // Create a new File object with the parent directory and new file name
        val newFile = File(parentDir, newFileName)

        // Rename the file
        if (!oldFile.renameTo(newFile)) {
            throw IllegalStateException("Failed to rename file: ${oldFile.absolutePath}")
        }

        return newFile
    }

}
