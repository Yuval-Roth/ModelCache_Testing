class TestsReporter {

    private val tests = mutableListOf<Test>()
    private val currentTest: Test
        get() = tests.last()

    fun startTest(testName: String, outputQueries: Boolean) {
        tests.add(Test(testName,outputQueries))
    }

    fun endTest(totalTime: Long) {
        currentTest.totalTime = totalTime
    }

    fun logInsertionTime(time: Long) {
        currentTest.insertionTime = time
    }

    fun logLookupTime(time: Long) {
        currentTest.lookupTime = time
    }

    fun logHit(query: String, response: String, time: Long) {
        currentTest.apply {
            hits.add(arrayOf(query,response, time.toString()))
            queryTimes.add(time)
            hitCount++
            queryCount++
            expectedQueryHitCount++
        }
    }

    fun logUnexpectedHit(query: String, response: String, expected: String, time: Long) {
        currentTest.apply {
            unexpectedHits.add(arrayOf(query, response, expected,time.toString()))
            queryTimes.add(time)
            hitCount++
            queryCount++
        }
    }

    fun logMiss(query: String, expected: String, time: Long) {
        currentTest.apply {
            misses.add(arrayOf(query,expected,time.toString()))
            queryTimes.add(time)
            queryCount++
        }
    }

    fun generateReport(): String {
        val report = StringBuilder()
        tests.forEachIndexed {
            index, test ->

            // Calculate metrics
            val hitRatio = (test.hitCount.toFloat() / test.queryCount) * 100
            val expectedHitQueryRatio = (test.expectedQueryHitCount.toFloat() / test.hitCount) * 100
            val throughput = test.queryCount / (test.lookupTime / 1000f)
            val meanLatency = test.queryTimes.average().toFloat()
            val sortedQueryTimes = test.queryTimes.sorted()
            val p95 = percentile(sortedQueryTimes, 0.95f)
            val p99 = percentile(sortedQueryTimes, 0.99f)

            report.appendLine("-------------------------------------------------------")
            report.appendLine()
            report.appendLine("Test number ${index + 1}")
            report.appendLine("Test name: ${test.testName}")
            report.appendLine()
            report.appendLine("Statistics:")
            report.appendLine()
            report.appendLine("Insertion time: ${test.insertionTime} ms")
            report.appendLine("Lookup time: ${test.lookupTime} ms")
            report.appendLine("Total time: ${test.totalTime} ms")
            report.appendLine("Hit ratio: ${test.hitCount}/${test.queryCount} ($hitRatio%)")
            report.appendLine("Expected query hit ratio: ${test.expectedQueryHitCount}/${test.hitCount} ($expectedHitQueryRatio%)")
            report.appendLine("Throughput: $throughput queries/second")
            report.appendLine("Mean request latency: $meanLatency ms")
            report.appendLine("95th percentile latency: $p95 ms")
            report.appendLine("99th percentile latency: $p99 ms")

            if(test.outputQueries){
                report.appendLine()
                report.appendLine("Hits:")
                report.appendLine()
                test.hits.forEach { (query, response, time) ->
                    report.appendLine("[X] query: $query")
                    report.appendLine("    hit: $response")
                    report.appendLine("    time: $time ms")
                }
                report.appendLine()
                report.appendLine("Unexpected hits:")
                report.appendLine()
                test.unexpectedHits.forEach { (query, response, expected, time) ->
                    report.appendLine("[?] query: $query")
                    report.appendLine("    hit: $response")
                    report.appendLine("    expected: $expected")
                    report.appendLine("    time: $time ms")
                }
                report.appendLine()
                report.appendLine("Misses:")
                report.appendLine()
                test.misses.forEach { (query, expected, time) ->
                    report.appendLine("[ ] query: $query")
                    report.appendLine("    expected: $expected")
                    report.appendLine("    time: $time ms")
                }
            }
            report.appendLine()
        }

        return report.toString()
    }

    private data class Test(
        val testName: String,
        val outputQueries: Boolean,
        var hitCount: Int = 0,
        var expectedQueryHitCount: Int = 0,
        var queryCount: Int = 0,
        val hits: MutableList<Array<String>> = mutableListOf(),
        val unexpectedHits: MutableList<Array<String>> = mutableListOf(),
        val misses: MutableList<Array<String>> = mutableListOf(),
        val queryTimes: MutableList<Long> = mutableListOf(),
        var lookupTime: Long = 0L,
        var insertionTime: Long = 0L,
        var totalTime: Long = 0L,
    )

    private fun percentile(sortedValues: List<Long>, percentile: Float): Float {
        if (sortedValues.isEmpty()) return 0f
        val index = percentile * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = kotlin.math.ceil(index).toInt()
        if (lower == upper) return sortedValues[lower].toFloat()
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }
}

