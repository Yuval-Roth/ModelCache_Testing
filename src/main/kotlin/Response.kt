import com.google.gson.annotations.SerializedName

data class Response(
    val errorCode: Int?,
    val errorDesc: String?,
    val writeStatus: String?,
    val cacheHit:Boolean?,
    @SerializedName("delta_time")
    val deltaTime: String?,
    @SerializedName("hit_query")
    val hitQuery: String?,
    val answer: String?,
    val response: Map<String,String>
)
