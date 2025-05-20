package utils

import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Serialize an object to JSON string.
 *
 * Uses [JsonUtils.serialize]
 */
fun Any.toJson(): String = JsonUtils.serialize(this)

/**
 * Deserialize a JSON string to an object of type T.
 *
 * Uses [JsonUtils.deserialize]
 */
inline fun <reified T> fromJson(json: String): T = JsonUtils.deserialize(json, T::class.java)

object JsonUtils {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .enableComplexMapKeySerialization()
        .create()

    /**
     * Serialize an object to JSON string.
     *
     * @param serializeStrings if true, serialize strings as JSON strings, otherwise strings are not serialized and are returned as is
     */
    fun serialize(obj: Any, serializeStrings: Boolean = false): String {
        if (! serializeStrings && obj is String) return obj
        return gson.toJson(obj)
    }

    fun <T> deserialize(json: String, type: Type): T {
        return gson.fromJson(json, type)
    }

    private class LocalDateTimeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        override fun serialize(src: LocalDateTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDateTime {
            return LocalDateTime.parse(json.asString)
        }
    }

    private class LocalTimeAdapter : JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
        override fun serialize(src: LocalTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalTime {
            return LocalTime.parse(json.asString)
        }
    }

    private class LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate {
            return LocalDate.parse(json.asString)
        }
    }
}

