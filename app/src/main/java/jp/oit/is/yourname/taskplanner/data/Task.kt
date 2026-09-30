package jp.oit.`is`.yourname.taskplanner.data

import java.time.LocalDate

data class Task(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val totalHours: Double,
    val done: Boolean = false,
)
