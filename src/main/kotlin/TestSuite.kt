import ModelCacheRequest.*
import com.sun.management.OperatingSystemMXBean
import utils.WebSocketClient
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
        get() = when(serverType.lowercase()) {
                "flask" -> ModelCache.flask()
                "fastapi" -> ModelCache.fastAPI()
                "websocket" -> ModelCache.websocket()
                else -> throw IllegalArgumentException("Invalid server type: $serverType")
            }

    init {
        loadData()

        // used to trigger the annoying slf4j debug message before the first test output
        if(serverType == "websocket") {
            WebSocketClient(URI("ws://localhost:5000/modelcache"))
        }
    }

    fun run(){
        try{
            test_insert_questions1_selfLookup_questions1()
            test_questions1_loaded_pairLookup_questions2()

            test_insert_questions2_selfLookup_questions2()
            test_questions2_loaded_pairLookup_questions1()

            println(getReport())

        } catch(e: Exception) {
            println("\nAn error occurred: \n${e.stackTraceToString()}\n")
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

    private fun buildQueries(sentences: Collection<String>): List<Query> {
        val queries = mutableListOf<Query>()
        for (sentence in sentences) {
            val queryEntry = QueryEntry(Role.USER, sentence)
            val query = Query(listOf(queryEntry), outputs[sentence]!!)
            queries.add(query)
        }
        return queries
    }

    private fun insert(sentences: Set<String>){
        cache.use { cache ->
            val queries = buildQueries(sentences)
            if (bulkInsertSupported) {
                cache.insert(queries)
            } else {
                for (query in queries) {
                    cache.insert(listOf(query))
                }
            }
        }
    }

    private fun lookup(sentences: Collection<String>, workers: Int, getExpectedHitQuery: (String) -> String) {
        val queries = buildQueries(sentences) as MutableList
        val queriesLock = Semaphore(1, true)
        val reportsLock = Semaphore(1, true)
        val threads = mutableListOf<Thread>()
        repeat(workers) {
            val t = Thread {
                cache.use { cache ->
                    while(true){
                        queriesLock.acquire()
                        if (queries.isEmpty()) {
                            queriesLock.release()
                            return@Thread
                        }
                        val query = queries.removeFirst()
                        queriesLock.release()
                        val content = query.query[0].content
                        val (response,time) = timer { cache.query(query.query) }
                        reportsLock.acquire()
                        handleQueryResponse(response, getExpectedHitQuery, content, time)
                        reportsLock.release()
                    }
                }
            }.apply{ start() }
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
        cache.use { cache->
            testsReporter.startTest(testName,outputQueries)
            if(clearCacheBefore) cache.clear()
            val (_,totalTime) = timer {
                val (_,insertionTime) = timer { insertion() }
                testsReporter.logInsertionTime(insertionTime)
                val (_,lookupTime) = timer { lookup() }
                testsReporter.logLookupTime(lookupTime)
            }
            if(clearCacheAfter) cache.clear()
            testRunning = false
            systemMetricsThread.interrupt()
            systemMetricsThread.join()
            testsReporter.endTest(totalTime)
            println("Done in ${totalTime}ms")
        }
    }

    // ========================================================================== |
    // ========================== TEST DEFINITIONS ============================== |
    // ========================================================================== |

    fun test_insert_questions1_selfLookup_questions1(){
        test(
            testName = "insert questions1 => self-lookup questions1",
            clearCacheBefore = true,
            insertion = { insert(questions1) },
            lookup = { lookup(questions1,workers){ s -> s} }
        )
    }
    fun test_questions1_loaded_pairLookup_questions2(){
        test(
            testName = "questions1 loaded => pair-lookup questions2",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { lookup(questions2,workers){ s -> pairs[s]!!} }
        )
    }
    fun test_insert_questions2_selfLookup_questions2(){
        test(
            testName = "insert questions2 => self-lookup questions2",
            clearCacheBefore = true,
            insertion = { insert(questions2) },
            lookup = { lookup(questions2,workers){ s -> s} }
        )
    }
    fun test_questions2_loaded_pairLookup_questions1(){
        test(
            testName = "questions2 loaded => pair-lookup questions1",
            outputQueries = false, // TODO: change to true when we want to see the output queries
            clearCacheAfter = true,
            lookup = { lookup(questions1,workers){ s -> pairs[s]!!} }
        )
    }
}

fun testSuite() {
    val serverType: String
    val enableBulkInsert: Boolean
    val workers: Int
    val queryPrefix: String

    val shouldNotHappen = IllegalStateException("Should not happen")

    println("Select test:")
    println("1. New system")
    println("2. Old system")
    println("3. Custom test")
    val testChoice = getChoice(3)
    when(testChoice){
        1 -> {
            enableBulkInsert = true
            queryPrefix = "user: "
            println("Select target server:")
            println("1. flask")
            println("2. fastAPI")
            println("3. Websocket")
            val serverChoice = getChoice(3)
            serverType = when (serverChoice) {
                1 -> "flask"
                2 -> "fastapi"
                3 -> "websocket"
                else -> throw shouldNotHappen
            }
            println("Worker count:")
            workers = getNumber()
        }
        2 -> {
            enableBulkInsert = false
            queryPrefix = "user###"
            workers = 1
            println("Select target server:")
            println("1. flask")
            println("2. fastAPI")
            val serverChoice = getChoice(2)
            serverType = when (serverChoice) {
                1 -> "flask"
                2 -> "fastapi"
                else -> throw shouldNotHappen
            }
        }
        3 -> {
            println("Select target server:")
            println("1. flask")
            println("2. fastAPI")
            println("3. Websocket")
            val serverChoice = getChoice(3)
            serverType = when (serverChoice) {
                1 -> "flask"
                2 -> "fastapi"
                3 -> "websocket"
                else -> throw shouldNotHappen
            }
            println("Enable bulk insert?")
            println("1. Yes")
            println("2. No")
            val bulkInsertChoice = getChoice(2)
            enableBulkInsert = when (bulkInsertChoice) {
                1 -> true
                2 -> false
                else -> throw shouldNotHappen
            }
            println("Query prefix:")
            println("1. new prefix ('user: ')")
            println("2. old prefix ('user###')")
            val queryPrefixChoice = getChoice(2)
            queryPrefix = when (queryPrefixChoice) {
                1 -> "user: "
                2 -> "user###"
                else -> throw shouldNotHappen
            }
            println("Worker count:")
            workers = getNumber()
        }
        else -> throw shouldNotHappen
    }

    println()
    println("Running test suite with the following configuration:")
    println("Target server  :  $serverType")
    println("Bulk insert    :  $enableBulkInsert")
    println("Query prefix   :  '$queryPrefix'")
    println("Workers count  :  $workers")
    println()

    TestSuite(
        bulkInsertSupported = enableBulkInsert,
        queryPrefix = queryPrefix,
        serverType = serverType,
        workers = workers
    ).run()
}