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
    val questions1 = HashSet<String>()
    val questions2 = HashSet<String>()
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
        val data = DataLoader.comqa_filtered()
        data.forEach {
            val question1 = it.question1
            val question2 = it.question2
            val answer = it.answer
            questions1.add(question1)
            questions2.add(question2)
            pairs[question1] = question2
            pairs[question2] = question1
            outputs[question1] = answer
            outputs[question2] = answer
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

    fun test_insert_questions1_selfLookup_questions1(){
        test(
            testName = "insert questions1 => self-lookup questions1",
            clearCacheBefore = true,
            insertion = { insert(questions1) },
            lookup = { BasicLookup(questions1,workers){ s -> s} }
        )
    }
    fun test_questions1_loaded_pairLookup_questions2(){
        test(
            testName = "questions1 loaded => pair-lookup questions2",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { BasicLookup(questions2,workers){ s -> pairs[s]!!} }
        )
    }
    fun test_insert_questions2_selfLookup_questions2(){
        test(
            testName = "insert questions2 => self-lookup questions2",
            clearCacheBefore = true,
            insertion = { insert(questions2) },
            lookup = { BasicLookup(questions2,workers){ s -> s} }
        )
    }
    fun test_questions2_loaded_pairLookup_questions1(){
        test(
            "questions2 loaded => pair-lookup questions1",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { BasicLookup(questions1,workers){ s -> pairs[s]!!} }
        )
    }
}

fun testSuite(queryPrefix: String, bulkInsertSupported: Boolean, serverType: String) {
    try{
        val tests = TestSuite(bulkInsertSupported, queryPrefix, serverType,6)

        tests.test_insert_questions1_selfLookup_questions1()
        tests.test_questions1_loaded_pairLookup_questions2()

        tests.test_insert_questions2_selfLookup_questions2()
        tests.test_questions2_loaded_pairLookup_questions1()

        println(tests.getReport())

    } catch(e: Exception) {
        println("\nAn error occurred: \n${e.stackTraceToString()}\n")
    }
}