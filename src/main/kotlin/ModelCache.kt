import ModelCacheRequest.Query
import ModelCacheRequest.QueryEntry
import ModelCacheRequest.RemoveType
import ModelCacheRequest.Type
import utils.RestApiClient
import utils.toJson

private const val URI = "http://127.0.0.1:5000/modelcache"

object ModelCache {

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
        val response = RestApiClient()
            .withUri(URI)
            .withHeader("Content-Type", "application/json")
            .withPost(request.toJson())
            .withHTTP1_1() // This is needed if we're using uvicorn (fastapi) as the server
            .send()
        return response
    }
}