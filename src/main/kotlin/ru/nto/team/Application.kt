package ru.nto.team

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
    runApplication<Application>(*args)
}
