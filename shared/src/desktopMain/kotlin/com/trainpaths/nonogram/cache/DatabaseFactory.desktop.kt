package com.trainpaths.nonogram.cache

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

class DesktopDatabaseFactory(private val dataDir: File) : DatabaseFactory {
    override suspend fun createDriver(): SqlDriver {
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${File(dataDir, "nonogram.db").absolutePath}",
            properties = Properties(),
            schema = NonogramDb.Schema.synchronous(),
        )
    }
}
