

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
            "2" -> testSuite()
            "3" -> {
                println("Exiting...")
                return
            }
            else -> println("Invalid choice. Please try again.")
        }
    }
}