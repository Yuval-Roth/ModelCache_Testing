import utils.JsonUtils
import java.util.Arrays

object DataLoader {

    fun comqa_train(): List<QA> {
        val text = this::class.java.getResource("/comqa_train.json")?.readText()
            ?: throw IllegalArgumentException("comqa_train.json not found in resources")
        val type = Array<ComQATemplate>::class.java
        val objs = JsonUtils.deserializePretty<Array<ComQATemplate>>(text,type)
        val output = Arrays.stream(objs)
            .filter { it.questions.size >= 2 }
            .map { obj ->
                val questionPairs = obj.questions.shuffled().take(2)
                val answer = obj.answers.first()
                QA(
                    question1 = questionPairs[0],
                    question2 = questionPairs[1],
                    answer = answer
                )
            }
            .toList()
        println(JsonUtils.serializePretty(output))
        return output
    }

    fun comqa_filtered(): List<QA> {
        val text = this::class.java.getResource("/comqa_filtered.json")?.readText()
            ?: throw IllegalArgumentException("comqa_filtered.json not found in resources")
        val type = Array<QA>::class.java
        val qas = JsonUtils.deserializePretty<Array<QA>>(text, type)
        return qas.toList()
    }

    fun chatgpt_generated(): List<QA> {
        val text = this::class.java.getResource("/chatgpt_generated.json")?.readText()
            ?: throw IllegalArgumentException("chatgpt_generated.json not found in resources")
        val type = Array<QA>::class.java
        val qas = JsonUtils.deserializePretty<Array<QA>>(text, type)
        return qas.toList()
    }

    private data class ComQATemplate(
        val clusterId: String,
        val questions: List<String>,
        val answers: List<String>
    )

}