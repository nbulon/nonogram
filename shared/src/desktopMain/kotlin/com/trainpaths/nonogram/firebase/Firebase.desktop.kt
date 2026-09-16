package com.trainpaths.nonogram.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import java.io.File
import java.util.Properties

/**
 * Boots gitlive's firebase-java-sdk. It has no Android storage to keep the signed-in user in, so
 * [FirebasePlatform] is backed by a properties file in [dataDir]; that is what makes sign-in survive a restart.
 */
object FirebaseDesktop {
    fun initialize(dataDir: File, apiKey: String, projectId: String, appId: String) {
        FirebasePlatform.initializeFirebasePlatform(FileStore(File(dataDir, "firebase.properties")))
        Firebase.initialize(
            Application(),
            FirebaseOptions(applicationId = appId, apiKey = apiKey, projectId = projectId),
        )
    }

    private class FileStore(private val file: File) : FirebasePlatform() {
        private val properties = Properties().apply {
            if (file.exists()) file.inputStream().use { load(it) }
        }

        override fun store(key: String, value: String) = synchronized(this) {
            properties.setProperty(key, value)
            save()
        }

        override fun retrieve(key: String): String? = synchronized(this) { properties.getProperty(key) }

        override fun clear(key: String) = synchronized(this) {
            properties.remove(key)
            save()
        }

        override fun log(msg: String) = println("Firebase: $msg")

        private fun save() = file.outputStream().use { properties.store(it, null) }
    }
}
