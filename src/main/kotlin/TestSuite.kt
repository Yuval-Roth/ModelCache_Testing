import ModelCache as cache
import ModelCacheRequest.*
import utils.fromJson

@Suppress("FunctionName")
class TestSuite {

    val pairs = HashMap<String,String>()
    val sentences1 = HashSet<String>()
    val sentences2 = HashSet<String>()
    val outputs = HashMap<String,String>()
    val testsReporter = TestsReporter()

    init {
        loadData()
    }

    fun getReport(): String {
        return testsReporter.generateReport()
    }

    private fun loadData(){
        val regex = Regex("\"([\\w'?\\s.,:!@#$%;/^&*+\\-°=’—]*)\"")
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
                if(! sentences1.add(sentence1) || ! sentences2.add(sentence2)){
                    println("Duplicate sentence found: $sentence1 or $sentence2")
                    continue
                }
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
        for(query in queries){
            val content = query.query[0].content
            val (returned,time) = timer { cache.query(query.query) }
            val response = fromJson<Response>(returned)
            val isHit = response.cacheHit!!
            if(isHit){
                val hitQuery = response.hitQuery!!.removePrefix("user: ")
                val expectedHitQuery = getExpectedHitQuery(content)
                if(expectedHitQuery.lowercase() == hitQuery.lowercase()) {
                    testsReporter.logHit(content,hitQuery,time)
                } else {
                    testsReporter.logUnexpectedHit(content, hitQuery, expectedHitQuery, time)
                }
            } else {
                testsReporter.logMiss(content,getExpectedHitQuery(content),time)
            }
        }
    }

    private fun <T> timer(run : () -> T): Pair<T,Long> {
        val startTime = System.currentTimeMillis()
        val output = run()
        val endTime = System.currentTimeMillis()
        return output to (endTime - startTime)
    }

    private fun test(
        testName: String,
        outputQueries: Boolean = false,
        clearCacheBefore: Boolean = false,
        clearCacheAfter: Boolean = false,
        insertion: () -> Unit = {},
        lookup: () -> Unit
    ){
        print("Running test: $testName .... ")
        testsReporter.startTest(testName,outputQueries)
        if(clearCacheBefore) cache.clear()
        val (_,totalTime) = timer {
            val (_,insertionTime) = timer { insertion() }
            testsReporter.logInsertionTime(insertionTime)
            val (_,lookupTime) = timer { lookup() }
            testsReporter.logLookupTime(lookupTime)
        }
        if(clearCacheAfter) cache.clear()
        testsReporter.endTest(totalTime)
        println("Done in ${totalTime}ms")
    }

    // ========================================================================== |
    // ========================== TEST DEFINITIONS ============================== |
    // ========================================================================== |

    fun test_insert_sentences1_selfLookup_sentences1(){
        test(
            testName = "insert sentences1 => self-lookup sentences1",
            clearCacheBefore = true,
            insertion = { insert(sentences1) },
            lookup = { lookup(sentences1){ s -> s} }
        )
    }
    fun test_sentences1_loaded_pairLookup_sentences2(){
        test(
            testName = "sentences1 loaded => pair-lookup sentences2",
            outputQueries = true,
            clearCacheAfter = true,
            lookup = { lookup(sentences2){ s -> pairs[s]!!} }
        )
    }
    fun test_insert_sentences2_selfLookup_sentences2(){
        test(
            testName = "insert sentences2 => self-lookup sentences2",
            clearCacheBefore = true,
            insertion = { insert(sentences2) },
            lookup = { lookup(sentences2){ s -> s} }
        )
    }
    fun test_sentences2_loaded_pairLookup_sentences1(){
        test(
            "sentences2 loaded => pair-lookup sentences1",
            outputQueries = true,
            clearCacheAfter = true,
            lookup = { lookup(sentences1){ s -> pairs[s]!!} }
        )
    }
}

fun testSuite(){
    try{
        val tests = TestSuite()

        tests.test_insert_sentences1_selfLookup_sentences1()
        tests.test_sentences1_loaded_pairLookup_sentences2()

        tests.test_insert_sentences2_selfLookup_sentences2()
        tests.test_sentences2_loaded_pairLookup_sentences1()

        println(tests.getReport())

    } catch(e: Exception) {
        println("\nAn error occurred: \n${e.stackTraceToString()}\n")
    }
}