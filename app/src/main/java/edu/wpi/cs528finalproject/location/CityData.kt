package edu.wpi.cs528finalproject.location

import com.beust.klaxon.Json

class CityData(
    @Json(name="City/Town")
    val cityTown: String = "Unknown",
    @Json(name="Total Case Counts")
    val totalCaseCounts: Int = 0,
    @Json(name="Two Week Case Counts")
    val twoWeekCaseCounts: Int = 0,
    @Json(name="Average Daily Rate")
    val averageDailyRate: Double = 0.0,
    @Json(name="% Change in Last Week")
    val percentChangeInLastWeek: String = "Unknown",
    @Json(name="Total Tests")
    val totalTests: Int = 0,
    @Json(name="Total Tests Last Two Weeks")
    val totalTestsLastTwoWeeks: Int = 0,
    @Json(name="Total Positive Tests")
    val totalPositiveTests: Int = 0,
    @Json(name="Percent Positivity")
    val percentPositivity: Double = 0.0,
    @Json(name="Change Since Last Week")
    val changeSinceLastWeek: String = "Unknown",
    @Json(name="Covid Level")
    val covidLevel: String = "Unknown"
)
