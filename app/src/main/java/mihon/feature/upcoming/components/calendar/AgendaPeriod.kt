package mihon.feature.upcoming.components.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal enum class AgendaPeriod {
    Week,
    Month,
    ;

    fun dates(anchor: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = when (this) {
        Week -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            start..start.plusDays(6)
        }

        Month -> anchor.withDayOfMonth(1)..anchor.with(TemporalAdjusters.lastDayOfMonth())
    }
}
