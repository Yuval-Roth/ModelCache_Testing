import ModelCache as cache
import ModelCacheRequest.*
import utils.fromJson

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

    private fun standardLookup(sentences: Set<String>) {
        val queries = buildQueries(sentences)
        // check how long it takes to lookup everything
        var hitCount = 0
        var sameHitQuery = 0
        for(query in queries){
            val returned = cache.query(query.query)
            val response = fromJson<Response>(returned)
            val isHit = response.cacheHit!!
            if(isHit){
                hitCount++
                val hitQuery = response.hitQuery!!.removePrefix("user###")
                val expectedHitQuery = pairs[query.query[0].content]
                if(expectedHitQuery == hitQuery) sameHitQuery++
            }
        }
        println("Hit Ratio: ${hitCount}/${queries.size} (${(hitCount.toFloat()/queries.size.toFloat())*100}%)")
        println("Expected query hit ratio: ${sameHitQuery}/${queries.size} (${(sameHitQuery.toFloat()/queries.size.toFloat())*100}%)")
    }

    private inline fun timer(run : () -> Unit): Long {
        val startTime = System.currentTimeMillis()
        run()
        return System.currentTimeMillis() - startTime
    }

    private fun test_template_insert_setntencex_lookup_sentencesy(sentencesx: Set<String>, sentencesy: Set<String>, testName: String) {
        println("----\nStarting test $testName...")
        cache.clear()
        val totalTime = timer {
            val insertionTime = timer { insert(sentencesx) }
            println("Insertion took $insertionTime ms")
            val lookupTime = timer { standardLookup(sentencesy) }
            println("Lookup took $lookupTime ms")
        }
        cache.clear()
        println("Total test time: $totalTime ms")
    }

    fun test_insert_sentences1_lookup_sentences1(){
        test_template_insert_setntencex_lookup_sentencesy(sentences1, sentences1, "test_insert_sentences1_lookup_sentences1")
    }
    fun test_insert_sentences2_lookup_sentences2(){
        test_template_insert_setntencex_lookup_sentencesy(sentences1, sentences2, "test_insert_sentences2_lookup_sentences2")
    }

    fun test_insert_sentences1_lookup_sentences2(){
        test_template_insert_setntencex_lookup_sentencesy(sentences1, sentences2, "test_insert_sentences1_lookup_sentences2")
    }

    fun test_insert_sentences2_lookup_sentences1(){
        test_template_insert_setntencex_lookup_sentencesy(sentences2, sentences1, "test_insert_sentences2_lookup_sentences1")
    }
}

fun main(){
    try{
        val tests = TestSuite()
        // both should be 100% hit ratio
//        tests.test_insert_sentences1_lookup_sentences1()
//        tests.test_insert_sentences2_lookup_sentences2()

        tests.test_insert_sentences1_lookup_sentences2()
        tests.test_insert_sentences2_lookup_sentences1()

    } catch(e: Exception) {
        println("\nAn error occurred: \n${e.stackTraceToString()}\n")
    }
}