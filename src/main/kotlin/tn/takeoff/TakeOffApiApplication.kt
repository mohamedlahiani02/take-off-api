package tn.takeoff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TakeOffApiApplication

fun main(args: Array<String>) {
	runApplication<TakeOffApiApplication>(*args)
}
