

class Main {
}

fun main() {
    while(true){
        println("Pick an option:")
        println("1. Run CLI")
        println("2. Run Test Suite")
        println("3. Exit")
        print(">> ")
        val choice = readlnOrNull() ?: continue
        when (choice) {
            "1" -> cli()
            "2" -> {
                println("Run tests for:")
                println("1. New system")
                println("2. Old system")
                print(">> ")
                val bulkInsertChoice = readlnOrNull() ?: continue
                when (bulkInsertChoice) {
                    "1" -> {
                        println("Select target server:")
                        println("1. flask")
                        println("2. fastAPI")
                        println("3. Websocket")
                        print(">> ")
                        val serverChoice = readlnOrNull() ?: continue
                        val serverType = when (serverChoice){
                            "1" -> "flask"
                            "2" -> "fastapi"
                            "3" -> "websocket"
                            else -> {
                                println("Invalid choice. Please try again.")
                                continue
                            }
                        }
                        println("Running test suite for new system ....")
                        testSuite(
                            queryPrefix = "user: ",
                            bulkInsertSupported = true,
                            serverType = serverType
                        )
                    }
                    "2" -> {
                        println("Select target server:")
                        println("1. flask")
                        println("2. fastAPI")
                        print(">> ")
                        val serverChoice = readlnOrNull() ?: continue
                        val serverType = when (serverChoice){
                            "1" -> "flask"
                            "2" -> "fastapi"
                            else -> {
                                println("Invalid choice. Please try again.")
                                continue
                            }
                        }
                        println("Running test suite for old system ....")
                        testSuite(
                            queryPrefix = "user###",
                            bulkInsertSupported = false,
                            serverType = serverType
                        )
                    }
                    else -> println("Invalid choice. Please try again.")
                }
            }
            "3" -> {
                println("Exiting...")
                return
            }
            else -> println("Invalid choice. Please try again.")
        }
    }
}