package app.screenplay.dto

data class ShotData(
    val sceneNumber: Int,
    val shotNumberInScene: Int,
    val location: String?,
    val durationSec: Int,
    val shotType: String,
    val description: String,
    val sound: String?,
    val props: String?,
    val equipment: String?
)

data class AnalyzedScene(
    val sceneNumber: Int,
    val originalText: String,
    val shots: List<ShotData>
)
