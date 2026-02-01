package com.stib.agent.data.models

data class ScheduleData(
    val day: String,
    val date: String,
    val service: String,
    val timeSlots: List<String> // Ex: ["06:30 - 11:51", "14:28 - 17:24"]
)
