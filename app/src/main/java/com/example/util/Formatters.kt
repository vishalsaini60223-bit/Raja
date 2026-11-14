package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val inrFormat = NumberFormat.getNumberInstance(
        Locale.Builder().setLanguage("en").setRegion("IN").build()
    ).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatInr(amount: Double): String {
        return "₹${inrFormat.format(amount)}"
    }

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    fun formatShortTime(timestamp: Long): String {
        return shortTimeFormat.format(Date(timestamp))
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val name = parts[0]
        val masked = if (name.length <= 3) name else name.take(3) + "***"
        return "$masked@${parts[1]}"
    }
}
