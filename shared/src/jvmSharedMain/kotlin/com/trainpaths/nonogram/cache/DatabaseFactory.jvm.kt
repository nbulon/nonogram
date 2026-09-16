package com.trainpaths.nonogram.cache

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/** Both JVM SQLite drivers (Android, JDBC) are synchronous and block, so they belong on the IO pool. */
internal actual val dbDispatcher: CoroutineContext = Dispatchers.IO
