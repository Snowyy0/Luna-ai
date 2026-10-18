package tn.luna.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
}
