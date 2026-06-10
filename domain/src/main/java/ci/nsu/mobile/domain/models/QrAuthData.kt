package ci.nsu.mobile.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class QrAuthData(
    val login: String,
    val password: String
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): QrAuthData? {
            return try {
                Json.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                null
            }
        }
    }
}