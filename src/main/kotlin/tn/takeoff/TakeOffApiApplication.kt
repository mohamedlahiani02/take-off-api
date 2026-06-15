package tn.takeoff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TakeOffApiApplication

fun main(args: Array<String>) {
	runApplication<TakeOffApiApplication>(*args)
}
