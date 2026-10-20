package tn.luna.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class ExamReminderTest {
    @Test
    fun examCountdownUsesCalendarDays() {
        val today = LocalDate.of(2026, 10, 18)
        val exam = LunaExam("exam-1", "Biology", "2026-10-25")

        assertEquals(7L, exam.daysUntil(today))
        assertTrue(isExamReminderDay(exam.daysUntil(today)))
    }

    @Test
    fun remindersOnlyRunDuringTheSevenDaysBeforeAndOnExamDay() {
        assertFalse(isExamReminderDay(8))
        for (days in 0L..7L) assertTrue(isExamReminderDay(days))
        assertFalse(isExamReminderDay(-1))
    }

    @Test
    fun anExamTodayAddedAfterTheMorningReminderIsScheduledImmediately() {
        val today = LocalDate.of(2026, 10, 18)

        assertEquals(today, firstExamReminderDate(today, LocalTime.of(10, 0), 0))
        assertEquals(today, firstExamReminderDate(today, LocalTime.of(8, 0), 0))
        assertEquals(today.plusDays(1), firstExamReminderDate(today, LocalTime.of(10, 0), 7))
        assertEquals(null, firstExamReminderDate(today, LocalTime.NOON, -1))
    }

    @Test
    fun calendarGridStartsWeeksOnTheSelectedWeekdayAndIncludesEveryDay() {
        val february = calendarWeeks(YearMonth.of(2024, 2), DayOfWeek.MONDAY)
        val november = calendarWeeks(YearMonth.of(2026, 11), DayOfWeek.MONDAY)

        assertEquals(5, february.size)
        assertEquals(null, february.first().first())
        assertEquals(LocalDate.of(2024, 2, 1), february[0][3])
        assertEquals(LocalDate.of(2024, 2, 29), february.last()[3])
        assertEquals(6, november.size)
        assertEquals(LocalDate.of(2026, 11, 1), november.first()[6])
        assertEquals(LocalDate.of(2026, 11, 30), november.last()[0])
    }
}
