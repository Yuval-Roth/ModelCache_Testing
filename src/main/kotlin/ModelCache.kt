import ModelCacheRequest.Query
import ModelCacheRequest.QueryEntry
import ModelCacheRequest.RemoveType
import ModelCacheRequest.Type
import utils.RestApiClient
import utils.WebSocketClient
import utils.toJson
import java.util.UUID

const val HOST = "localhost:5000/modelcache"

class ModelCache(
    private val sendRequestFunction: (ModelCacheRequest) -> String
) {

    fun insert(chatInfo: List<Query>): String {
        val request = ModelCacheRequest(
            type = Type.INSERT,
            chatInfo = chatInfo
        )
        return sendRequest(request)
    }

    fun query(query: List<QueryEntry>): String {
        val request = ModelCacheRequest(
            type = Type.QUERY,
            query = query
        )
        return sendRequest(request)
    }

    fun clear(): String {
        val request = ModelCacheRequest(
            type = Type.REMOVE,
            removeType = RemoveType.TRUNCATE_BY_MODEL
        )
        return sendRequest(request)
    }

    private fun sendRequest(request: ModelCacheRequest): String {
        return sendRequestFunction(request)
    }

    companion object {
        fun flask() = ModelCache { request ->
            val response = RestApiClient()
                .withUri("http://$HOST")
                .withHeader("Content-Type", "application/json")
                .withPost(request.toJson())
                .send()
            return@ModelCache response
        }
        fun fastAPI() = ModelCache { request ->
            val response = RestApiClient()
                .withUri("http://$HOST")
                .withHeader("Content-Type", "application/json")
                .withPost(request.toJson())
                .withHTTP1_1() // uvicorn uses HTTP/1.1
                .send()
            return@ModelCache response
        }
        fun websocket(ws: WebSocketClient) = ModelCache { request ->
            val id = UUID.randomUUID().toString()
            val toSend = WebSocketRequest(id, request).toJson()
            ws.send(toSend)
            id
        }
    }
}