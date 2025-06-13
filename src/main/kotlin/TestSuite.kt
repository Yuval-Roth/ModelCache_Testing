import ModelCacheRequest.*
import com.sun.management.OperatingSystemMXBean
import utils.WebSocketClient
import utils.fromJson
import java.lang.management.ManagementFactory
import java.net.URI
import java.util.concurrent.Semaphore

@Suppress("FunctionName")
class TestSuite(
    val bulkInsertSupported: Boolean,
    val queryPrefix: String,
    val serverType: String,
    val workers: Int
) {
    val pairs = HashMap<String,String>()
    val sentences1 = HashSet<String>()
    val sentences2 = HashSet<String>()
    val outputs = HashMap<String,String>()
    val testsReporter = TestsReporter()
    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val cache: ModelCache
    val ws: WebSocketClient by lazy {
        WebSocketClient(URI("ws://$HOST")).apply{ connectBlocking() }
    }

    init {
        loadData()
        cache = when(serverType.lowercase()) {
            "flask" -> ModelCache.flask()
            "fastapi" -> ModelCache.fastAPI()
            "websocket" -> ModelCache.websocket(ws)
            else -> throw IllegalArgumentException("Invalid server type: $serverType")
        }
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

    private fun buildQueries(sentences: Set<String>): List<Query> {
        val queries = mutableListOf<Query>()
        for (sentence in sentences) {
            val queryEntry = QueryEntry(Role.USER, sentence)
            val query = Query(listOf(queryEntry), outputs[sentence]!!)
            queries.add(query)
        }
        return queries
    }

    private fun insert(sentences: Set<String>) = when(serverType.lowercase()){
        "websocket" -> insertWs(sentences)
        "flask", "fastapi" -> insertRest(sentences)
        else  -> throw IllegalArgumentException("Invalid server type: $serverType")
    }

    private fun insertWs(sentences: Set<String>){
        val queries = buildQueries(sentences)
        cache.insert(queries)
        ws.nextMessageBlocking()
    }

    private fun insertRest(sentences: Set<String>){
        val queries  = buildQueries(sentences)
        if(bulkInsertSupported){
            cache.insert(queries)
        } else {
            for(query in queries){
                cache.insert(listOf(query))
            }
        }
    }

    private fun BasicLookup(sentences: Set<String>, workers: Int, getExpectedHitQuery: (String) -> String) = when(serverType.lowercase()) {
        "websocket" -> lookupWs(sentences, workers,getExpectedHitQuery)
        "flask", "fastapi" -> lookupRest(sentences, workers,getExpectedHitQuery)
        else -> throw IllegalArgumentException("Invalid server type: $serverType")
    }

    private fun lookupWs(sentences: Set<String>, workers: Int, getExpectedHitQuery: (String) -> String) {
        val queries = buildQueries(sentences) as MutableList
        val queriesLock = Semaphore(1, true)
        val reportsLock = Semaphore(1, true)
        val threads = mutableListOf<Thread>()
        repeat(workers) {
            val t = Thread {
                val ws = WebSocketClient(URI("ws://$HOST")).apply{ connectBlocking() }
                val cache = ModelCache.websocket(ws)
                while(true){
                    queriesLock.acquire()
                    if (queries.isEmpty()) {
                        queriesLock.release()
                        ws.closeBlocking()
                        return@Thread
                    }
                    val query = queries.removeFirst()
                    queriesLock.release()
                    val content = query.query[0].content
                    val (returned,time) = timer {
                        cache.query(query.query)
                        ws.nextMessageBlocking()
                    }
                    val wsResponse = fromJson<WebSocketResponse>(returned)
                    val response = wsResponse.result
                    reportsLock.acquire()
                    handleQueryResponse(response, getExpectedHitQuery, content, time)
                    reportsLock.release()
                }
            }.apply{ start() }
            threads.add(t)
        }
        for (thread in threads) {
            thread.join()
        }
    }

    private fun lookupRest(sentences: Set<String>, workers: Int, getExpectedHitQuery: (String) -> String) {
        val queries = buildQueries(sentences) as MutableList
        val queriesLock = Semaphore(1, true)
        val reportsLock = Semaphore(1, true)
        val threads = mutableListOf<Thread>()
        repeat(workers) {
            val t = Thread {
                while(true){
                    queriesLock.acquire()
                    if (queries.isEmpty()) {
                        queriesLock.release()
                        return@Thread
                    }
                    val query = queries.removeFirst()
                    queriesLock.release()
                    val content = query.query[0].content
                    val (returned,time) = timer { cache.query(query.query) }
                    val response = fromJson<Response>(returned)
                    reportsLock.acquire()
                    handleQueryResponse(response, getExpectedHitQuery, content, time)
                    reportsLock.release()
                }
            }.apply {start()}
            threads.add(t)
        }
        for (thread in threads) {
            thread.join()
        }
    }

    private fun handleQueryResponse(
        response: Response,
        getExpectedHitQuery: (String) -> String,
        content: String,
        time: Long
    ) {
        val isHit = response.cacheHit!!
        if (isHit) {
            val hitQuery = response.hitQuery!!.removePrefix(queryPrefix)
            val expectedHitQuery = getExpectedHitQuery(content)
            if (expectedHitQuery.lowercase() == hitQuery.lowercase()) {
                testsReporter.logHit(content, hitQuery, time)
            } else {
                testsReporter.logUnexpectedHit(content, hitQuery, expectedHitQuery, time)
            }
        } else {
            testsReporter.logMiss(content, getExpectedHitQuery(content), time)
        }
    }

    private fun <T> timer(run : () -> T): Pair<T,Long> {
        val startTime = System.currentTimeMillis()
        val output = run()
        val endTime = System.currentTimeMillis()
        return output to (endTime - startTime)
    }

    private fun captureSystemMetrics() {
        var cpuUsagePercentage: Float
        do {
            cpuUsagePercentage = (osBean.cpuLoad * 100).toFloat()
        } while (cpuUsagePercentage < 0)

        val totalMemorySize = osBean.totalMemorySize
        val freeMemorySize = osBean.freeMemorySize
        val usedMemory = totalMemorySize - freeMemorySize
        val memoryUsagePercentage = ((usedMemory.toFloat() / totalMemorySize) * 100)
        testsReporter.logSystemMetrics(cpuUsagePercentage,memoryUsagePercentage)
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
        var testRunning = true
        val systemMetricsThread = Thread {
            Thread.sleep(1000)
            while (testRunning) {
                captureSystemMetrics()
                try{
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }.apply { start() }
        testsReporter.startTest(testName,outputQueries)
        if(clearCacheBefore){
            cache.clear()
            if(serverType == "websocket") ws.nextMessageBlocking()
        }
        val (_,totalTime) = timer {
            val (_,insertionTime) = timer { insertion() }
            testsReporter.logInsertionTime(insertionTime)
            val (_,lookupTime) = timer { lookup() }
            testsReporter.logLookupTime(lookupTime)
        }
        if(clearCacheAfter){
            cache.clear()
            if(serverType == "websocket") ws.nextMessageBlocking()
        }
        testRunning = false
        systemMetricsThread.interrupt()
        systemMetricsThread.join()
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
            lookup = { BasicLookup(sentences1,workers){ s -> s} }
        )
    }
    fun test_sentences1_loaded_pairLookup_sentences2(){
        test(
            testName = "sentences1 loaded => pair-lookup sentences2",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { BasicLookup(sentences2,workers){ s -> pairs[s]!!} }
        )
    }
    fun test_insert_sentences2_selfLookup_sentences2(){
        test(
            testName = "insert sentences2 => self-lookup sentences2",
            clearCacheBefore = true,
            insertion = { insert(sentences2) },
            lookup = { BasicLookup(sentences2,workers){ s -> s} }
        )
    }
    fun test_sentences2_loaded_pairLookup_sentences1(){
        test(
            "sentences2 loaded => pair-lookup sentences1",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { BasicLookup(sentences1,workers){ s -> pairs[s]!!} }
        )
    }
}

fun testSuite(queryPrefix: String, bulkInsertSupported: Boolean, serverType: String) {
    try{
        val tests = TestSuite(bulkInsertSupported, queryPrefix, serverType,2)

        tests.test_insert_sentences1_selfLookup_sentences1()
        tests.test_sentences1_loaded_pairLookup_sentences2()

        tests.test_insert_sentences2_selfLookup_sentences2()
        tests.test_sentences2_loaded_pairLookup_sentences1()

        println(tests.getReport())

    } catch(e: Exception) {
        println("\nAn error occurred: \n${e.stackTraceToString()}\n")
    }
}