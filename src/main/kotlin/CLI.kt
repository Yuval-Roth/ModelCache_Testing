import ModelCacheRequest.*
import ModelCache as cache
fun main() {
    while(true){
        try{
            println("Welcome to ModelCache example CLI")
            println("Select an option:")
            println("1. Insert to cache")
            println("2. Query cache")
            println("3. Clear cache")
            println("4. Exit")
            print(">> ")
            val choice = readlnOrNull() ?: continue
            when (choice) {
                "1" -> {
                    val chatInfo = mutableListOf<Query>()
                    while(true){
                        val entries = getQueryEntries()
                        println()
                        println("Enter output:")
                        print(">> ")
                        val output = readlnOrNull()
                        if (output.isNullOrBlank()) {
                            println("Output cannot be empty. Please try again.")
                            continue
                        }
                        chatInfo.add(Query(entries, output))
                        println()
                        println("Do you want to add more entries? (y/n)")
                        print(">> ")
                        val addMore = readlnOrNull()
                        if (addMore.equals("n", ignoreCase = true)) {
                            break
                        } else if (!addMore.equals("y", ignoreCase = true)) {
                            println("Invalid choice. Please try again.")
                            continue
                        }
                    }
                    println()
                    println("Inserting to cache...")
                    println("Response: ${cache.insert(chatInfo)}")
                    println()
                }
                "2" -> {
                    val entries = getQueryEntries()
                    println()
                    println("Querying cache...")
                    println("Response: ${cache.query(entries)}")
                    println()
                }
                "3" -> {
                    val response = cache.clear()
                    println()
                    println("Clearing cache...")
                    println("Response: $response")
                    println()
                }
                "4" -> {
                    println()
                    println("Exiting...")
                    return
                }
                else -> {
                    println()
                    println("Invalid choice. Please try again.")
                    println()
                }
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}\n")
        }
    }
}

private fun getQueryEntries(): List<QueryEntry> {
    println()
    println("Enter input data:")
    println()
    val entries = mutableListOf<QueryEntry>()
    var firstTime = true
    while (true) {
        println("Role:")
        println("1. User")
        println("2. System")
        if(! firstTime) println("3. Done")
        print(">> ")
        val roleChoice = readlnOrNull() ?: continue
        when (roleChoice) {
            "1", "2" -> {
                firstTime = false
            }
            "3" -> {
                if(!firstTime) {
                    break
                } else {
                    println("Invalid choice. Please try again.")
                    continue
                }
            }
            else -> {
                println("Invalid choice. Please try again.")
                continue
            }
        }
        println("Enter message:")
        print(">> ")
        val message = readlnOrNull()
        if (message.isNullOrBlank()) {
            println("Message cannot be empty. Please try again.")
            continue
        }
        entries.add(QueryEntry(Role.entries[roleChoice.toInt() - 1], message))
        println()
    }
    return entries
}
