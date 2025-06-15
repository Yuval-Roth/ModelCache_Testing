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

    fun logSystemMetrics(cpuUsage: Float, memoryUsage: Float) {
        currentTest.apply {
            cpuUsages.add(cpuUsage)
            memoryUsages.add(memoryUsage)
        }
    }

    fun generateReport(): String {
        val report = StringBuilder()
        tests.forEachIndexed {
            index, test ->

            // Calculate metrics
            val hitRatio = if(test.queryCount == 0) 0 else (test.hitCount.toFloat() / test.queryCount) * 100
            val expectedHitQueryRatio = if(test.hitCount == 0) 0 else (test.expectedQueryHitCount.toFloat() / test.hitCount) * 100
            val throughput = test.queryCount / (test.lookupTime / 1000f)
            val meanLatency = test.queryTimes.average().toFloat()
            val sortedQueryTimes = test.queryTimes.sorted()
            val p95QueryTime = percentileLong(sortedQueryTimes, 0.95f)
            val p99QueryTime = percentileLong(sortedQueryTimes, 0.99f)
            val sortedMemoryUsages = test.memoryUsages.sorted()
            val meanMemoryUsage = test.memoryUsages.average().toFloat()
            val p95MemoryUsage = percentileFloat(sortedMemoryUsages, 0.95f)
            val p99MemoryUsage = percentileFloat(sortedMemoryUsages, 0.99f)
            val sortedCpuUsages = test.cpuUsages.sorted()
            val meanCpuUsage = test.cpuUsages.average().toFloat()
            val p95CpuUsage = percentileFloat(sortedCpuUsages, 0.95f)
            val p99CpuUsage = percentileFloat(sortedCpuUsages, 0.99f)

            report.appendLine("-------------------------------------------------------")
            report.appendLine()
            report.appendLine("Test number ${index + 1}")
            report.appendLine("Test name: ${test.testName}")
            report.appendLine()
            report.appendLine("Statistics:")
            report.appendLine()
            report.appendLine("Insertion time: ${test.insertionTime} ms")
            report.appendLine("Querying time: ${test.lookupTime} ms")
            report.appendLine("Total time: ${test.totalTime} ms")
            report.appendLine("Hit ratio: ${test.hitCount}/${test.queryCount} ($hitRatio%)")
            report.appendLine("Expected query hit ratio: ${test.expectedQueryHitCount}/${test.hitCount} ($expectedHitQueryRatio%)")
            report.appendLine("Query throughput: $throughput queries/second")
            report.appendLine("Mean query latency: $meanLatency ms")
            report.appendLine("p95 query latency: $p95QueryTime ms")
            report.appendLine("p99 percentile query latency: $p99QueryTime ms")
            report.appendLine("Mean memory usage: $meanMemoryUsage%")
            report.appendLine("p95 memory usage: $p95MemoryUsage%")
            report.appendLine("p99 memory usage: $p99MemoryUsage%")
            report.appendLine("Mean CPU usage: $meanCpuUsage%")
            report.appendLine("p95 CPU usage: $p95CpuUsage%")
            report.appendLine("p99 CPU usage: $p99CpuUsage%")
            report.appendLine()

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
        var lookupTime: Long = 0L,
        var insertionTime: Long = 0L,
        var totalTime: Long = 0L,
        val hits: MutableList<Array<String>> = mutableListOf(),
        val unexpectedHits: MutableList<Array<String>> = mutableListOf(),
        val misses: MutableList<Array<String>> = mutableListOf(),
        val queryTimes: MutableList<Long> = mutableListOf(),
        val memoryUsages: MutableList<Float> = mutableListOf(),
        val cpuUsages: MutableList<Float> = mutableListOf(),
    )

    private fun percentileLong(sortedValues: List<Long>, percentile: Float): Float {
        if (sortedValues.isEmpty()) return 0f
        val index = percentile * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = kotlin.math.ceil(index).toInt()
        if (lower == upper) return sortedValues[lower].toFloat()
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }

    private fun percentileFloat(sortedValues: List<Float>, percentile: Float): Float {
        if (sortedValues.isEmpty()) return 0f
        val index = percentile * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = kotlin.math.ceil(index).toInt()
        if (lower == upper) return sortedValues[lower]
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }
}

