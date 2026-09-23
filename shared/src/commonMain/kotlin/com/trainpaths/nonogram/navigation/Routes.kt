package com.trainpaths.nonogram.navigation

import kotlinx.serialization.Serializable

@Serializable
object LoginRoute

@Serializable
object MenuRoute

@Serializable
object GenListRoute

@Serializable
data class GenConfRoute(val editing: Boolean = false)

@Serializable
object GeneratorRoute

@Serializable
object GenScanRoute

@Serializable
data class FilterRoute(val generator: Boolean)

@Serializable
object SettingsRoute

@Serializable
object AdminRoute

@Serializable
data class GameRoute(val nonogramId: Long)

@Serializable
data class PlayDialogRoute(val nonogramId: Long, val difficulty: String)

@Serializable
data class ArtRoute(val nonogramId: Long, val won: Boolean)