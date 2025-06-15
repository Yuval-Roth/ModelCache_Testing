fun main() {
    while(true){
        println("Pick an option:")
        println("1. Run CLI")
        println("2. Run Test Suite")
        println("3. Exit")
        val choice = getChoice(3)
        when (choice) {
            1 -> cli()
            2 -> testSuite()
            3 -> {
                println("Exiting...")
                return
            }
        }
    }
}


fun getChoice(count: Int): Int {
    val choices = mutableSetOf<String>()
    for(i in 1..count) {
        choices.add(i.toString())
    }
    fun badChoice() {
        println("Invalid choice. Please try again.")
    }
    while(true){
        print(">> ")
        val input = readlnOrNull()
        if(input.isNullOrBlank()) {
            badChoice()
            continue
        }
        if(input in choices) {
            return input.toInt()
        } else {
            badChoice()
        }
    }
}

fun getNumber(): Int {
    while (true) {
        print(">> ")
        val input = readlnOrNull()
        if (input.isNullOrBlank()) {
            println("Input cannot be empty. Please try again.")
            continue
        }
        val number = input.toIntOrNull()
        if (number != null) {
            return number
        } else {
            println("Invalid number. Please try again.")
        }
    }
}

