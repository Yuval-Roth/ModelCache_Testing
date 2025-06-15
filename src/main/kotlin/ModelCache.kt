import ModelCacheRequest.Query
import ModelCacheRequest.QueryEntry
import ModelCacheRequest.RemoveType
import ModelCacheRequest.Type
import utils.RestApiClient
import utils.WebSocketClient
import utils.fromJson
import utils.toJson
import java.lang.AutoCloseable
import java.net.URI
import java.util.UUID

const val HOST = "localhost:5000/modelcache"

class ModelCache(
    private val onClose: () -> Unit = {},
    private val sendRequestFunction: (ModelCacheRequest) -> Response
): AutoCloseable {

    fun insert(chatInfo: List<Query>): Response {
        val request = ModelCacheRequest(
            type = Type.INSERT,
            chatInfo = chatInfo
        )
        return sendRequest(request)
    }

    fun query(query: List<QueryEntry>): Response {
        val request = ModelCacheRequest(
            type = Type.QUERY,
            query = query
        )
        return sendRequest(request)
    }

    fun clear(): Response {
        val request = ModelCacheRequest(
            type = Type.REMOVE,
            removeType = RemoveType.TRUNCATE_BY_MODEL
        )
        return sendRequest(request)
    }

    private fun sendRequest(request: ModelCacheRequest): Response {
        return sendRequestFunction(request)
    }

    override fun close() {
        onClose()
    }

    companion object {
        fun flask() = ModelCache { request ->
            val response = RestApiClient()
                .withUri("http://$HOST")
                .withHeader("Content-Type", "application/json")
                .withPost(request.toJson())
                .send()
            fromJson<Response>(response)
        }
        fun fastAPI() = ModelCache { request ->
            val response = RestApiClient()
                .withUri("http://$HOST")
                .withHeader("Content-Type", "application/json")
                .withPost(request.toJson())
                .withHTTP1_1() // uvicorn uses HTTP/1.1
                .send()
            fromJson<Response>(response)
        }
        fun websocket(): ModelCache {
            val ws = WebSocketClient(URI("ws://$HOST")).apply{ connectBlocking() }
            return ModelCache({ ws.closeBlocking() }) { request ->
                val id = UUID.randomUUID().toString()
                val toSend = WebSocketRequest(id, request).toJson()
                ws.send(toSend)
                val returned = ws.nextMessageBlocking()
                val wsResponse = fromJson<WebSocketResponse>(returned)
                wsResponse.result
            }
        }
    }
}