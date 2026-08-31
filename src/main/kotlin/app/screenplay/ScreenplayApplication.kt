package app.screenplay

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ScreenplayApplication

fun main(args: Array<String>) {
	runApplication<ScreenplayApplication>(*args)
}
