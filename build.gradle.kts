// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

tasks.register("verifyWrapperJar") {
    doLast {
        val jarFile = file("gradle/wrapper/gradle-wrapper.jar")
        if (!jarFile.exists()) {
            throw GradleException("gradle-wrapper.jar does not exist!")
        }
        println("File size: ${jarFile.length()} bytes")
        
        // Compute SHA-256
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(jarFile.readBytes())
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
        println("SHA-256: $sha256")
        
        // List ZIP entries to verify integrity
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                println("Valid ZIP Archive. Entries:")
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements() && count < 15) {
                    val entry = entries.nextElement()
                    println("  - ${entry.name} (${entry.size} bytes)")
                    count++
                }
                if (entries.hasMoreElements()) {
                    println("  ... and more")
                }
            }
        } catch (e: Exception) {
            throw GradleException("gradle-wrapper.jar is corrupt or invalid ZIP!", e)
        }

        // Verify and set executable permission on gradlew
        val gradlewFile = file("gradlew")
        if (gradlewFile.exists()) {
            val isExecutable = gradlewFile.canExecute()
            println("gradlew currently executable: $isExecutable")
            val success = gradlewFile.setExecutable(true, false)
            println("gradlew setExecutable(true, false) success: $success")
            println("gradlew now executable: ${gradlewFile.canExecute()}")
        } else {
            println("WARNING: gradlew script does not exist!")
        }
    }
}
