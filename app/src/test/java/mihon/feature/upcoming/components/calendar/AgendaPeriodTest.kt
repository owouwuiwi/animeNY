package mihon.feature.upcoming.components.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AgendaPeriodTest {
    @Test
    fun `a week crosses the year boundary in the local weekday order`() {
        val anchor = LocalDate.of(2027, 1, 1)

        assertEquals(
            LocalDate.of(2026, 12, 28)..LocalDate.of(2027, 1, 3),
            AgendaPeriod.Week.dates(anchor, DayOfWeek.MONDAY),
        )
        assertEquals(
            LocalDate.of(2026, 12, 27)..LocalDate.of(2027, 1, 2),
            AgendaPeriod.Week.dates(anchor, DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `month boundaries include leap day`() {
        assertEquals(
            LocalDate.of(2028, 2, 1)..LocalDate.of(2028, 2, 29),
            AgendaPeriod.Month.dates(LocalDate.of(2028, 2, 15), DayOfWeek.MONDAY),
        )
    }
}
