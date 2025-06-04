import ModelCache as cache
import ModelCacheRequest.*
import utils.fromJson
import kotlin.system.measureTimeMillis

class TestSuite {

    val pairs = HashMap<String,String>()
    val sentences1 = HashSet<String>()
    val sentences2 = HashSet<String>()
    val outputs = HashMap<String,String>()

    fun loadData(){
        val dataStream = this.javaClass.getResourceAsStream("test_data.txt")
        dataStream.bufferedReader().use { reader ->
            val lines = reader.readLines()
            for (line in lines) {
                val (sentence1,sentence2,response) = line.split(",")
                sentences1.add(sentence1)
                sentences2.add(sentence2)
                pairs[sentence1] = sentence2
                pairs[sentence2] = sentence1
                outputs[sentence1] = response
                outputs[sentence2] = response
            }
        }
    }

    fun run(){

    }

    fun standardLookup() {
        //insert the sentences1
        val queryEntries = mutableListOf<QueryEntry>()
        val queries = mutableListOf<Query>()
        for (sentence1 in sentences1){
            val queryEntry = QueryEntry(Role.USER,sentence1)
            queryEntries.add(queryEntry)
            val query = Query(listOf(queryEntry),outputs[sentence1]!!)
            queries.add(query)
//            cache.insert(listOf(query))
        }
        cache.insert(queries)

        // check how long it takes to lookup everything
        val results = mutableMapOf<String,Pair<Boolean,Float>>()
        var totalTime = 0f
        for(queryEntry in queryEntries){
            val returned = cache.query(listOf(queryEntry))
            val response = fromJson<Response>(returned)
            val isHit = response.cacheHit!!
            val deltaTime = response.deltaTime!!.substring(0,response.deltaTime.length-1).toFloat()
            totalTime += deltaTime
            results[queryEntry.content] = isHit to deltaTime
            println("${queryEntry.content}: ${if(isHit) "hit" else "miss"}, deltaTime: $deltaTime")
        }
        cache.clear()

        println(totalTime)
    }
}

fun main(){
    val tests = TestSuite()
    tests.loadData()
    tests.standardLookup()
}