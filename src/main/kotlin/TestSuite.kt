import ModelCache as cache
import ModelCacheRequest.*
import utils.fromJson

@Suppress("FunctionName")
class TestSuite {

    val pairs = HashMap<String,String>()
    val sentences1 = HashSet<String>()
    val sentences2 = HashSet<String>()
    val outputs = HashMap<String,String>()

    init {
        loadData()
    }

    private fun loadData(){
        val regex = Regex("\"([\\w'?\\s.,:!@#$%^&*+\\-°=]*)\"")
        val dataStream = this.javaClass.getResourceAsStream("test_data.txt")!!
        dataStream.bufferedReader().use { reader ->
            val lines = reader.readLines()
            for (line in lines) {
                val matches = regex.findAll(line).map { it.groupValues[1] }.toList()
                if(matches.size != 3){
                    println("Input not matched correctly: $line")
                    continue
                }
                val (sentence1,sentence2,response) = matches
                sentences1.add(sentence1)
                sentences2.add(sentence2)
                pairs[sentence1] = sentence2
                pairs[sentence2] = sentence1
                outputs[sentence1] = response
                outputs[sentence2] = response
            }
        }
    }

    private fun insert(sentences: Set<String>){
        val queries  = buildQueries(sentences)
        cache.insert(queries)
    }

    private fun buildQueries(sentences: Set<String>): List<Query> {
        val queries = mutableListOf<Query>()
        for (sentence in sentences) {
            val queryEntry = QueryEntry(Role.USER, sentence)
            val query = Query(listOf(queryEntry), outputs[sentence]!!)
            queries.add(query)
        }
        return queries
    }

    private fun lookup(sentences: Set<String>, getExpectedHitQuery: (String) -> String) {
        val queries = buildQueries(sentences)
        var hitCount = 0
        var sameHitQuery = 0
        for(query in queries){
            val content = query.query[0].content
            val returned = cache.query(query.query)
            val response = fromJson<Response>(returned)
            val isHit = response.cacheHit!!
            if(isHit){
                hitCount++
                val hitQuery = response.hitQuery!!.removePrefix("user###")
                val expectedHitQuery = getExpectedHitQuery(content)
                if(expectedHitQuery == hitQuery) sameHitQuery++
            }
        }
        println("Hit Ratio: ${hitCount}/${queries.size} (${(hitCount.toFloat()/queries.size.toFloat())*100}%)")
        println("Expected query hit ratio: ${sameHitQuery}/${queries.size} (${(sameHitQuery.toFloat()/queries.size.toFloat())*100}%)")
    }

    private fun timer(run : () -> Unit): Long {
        val startTime = System.currentTimeMillis()
        run()
        return System.currentTimeMillis() - startTime
    }

    private fun test(testName:String, clearCacheBefore:Boolean,clearCacheAfter: Boolean, setup: () -> Unit, test: () -> Unit){
        println("----\nStarting test $testName...")
        if(clearCacheBefore) cache.clear()
        val totalTime = timer {
            val insertionTime = timer { setup() }
            println("Setup took $insertionTime ms")
            val lookupTime = timer { test() }
            println("Test took $lookupTime ms")
        }
        if(clearCacheAfter) cache.clear()
        println("Total test time: $totalTime ms")
    }

    // ========================================================================== |
    // ========================== TEST DEFINITIONS ============================== |
    // ========================================================================== |

    fun test_insert_sentences1_selfLookup_sentences1(){
        test(
            "insert_sentences1_selfLookup_sentences1",
            clearCacheBefore = true,
            clearCacheAfter = false,
            setup = { insert(sentences1) },
            test = { lookup(sentences1){s -> s} }
        )
    }
    fun test_sentences1_loaded_pairLookup_sentences2(){
        test(
            "sentences1_loaded_pairLookup_sentences2",
            clearCacheBefore = false,
            clearCacheAfter = true,
            setup = { },
            test = { lookup(sentences2){ s -> pairs[s]!!} }
        )
    }
    fun test_insert_sentences2_selfLookup_sentences2(){
        test(
            "insert_sentences2_selfLookup_sentences2",
            clearCacheBefore = true,
            clearCacheAfter = false,
            setup = { insert(sentences2) },
            test = { lookup(sentences2){s -> s} }
        )
    }
    fun test_sentences2_loaded_pairLookup_sentences1(){
        test(
            "sentences2_loaded_pairLookup_sentences1",
            clearCacheBefore = false,
            clearCacheAfter = true,
            setup = { },
            test = { lookup(sentences1){ s -> pairs[s]!!} }
        )
    }
}

fun main(){
    try{
        val tests = TestSuite()

        tests.test_insert_sentences1_selfLookup_sentences1()
        tests.test_sentences1_loaded_pairLookup_sentences2()

        tests.test_insert_sentences2_selfLookup_sentences2()
        tests.test_sentences2_loaded_pairLookup_sentences1()

    } catch(e: Exception) {
        println("\nAn error occurred: \n${e.stackTraceToString()}\n")
    }
}