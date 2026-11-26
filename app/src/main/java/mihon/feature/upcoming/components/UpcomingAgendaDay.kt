package mihon.feature.upcoming.components

import kotlinx.datetime.LocalDate

data class UpcomingAgendaDay<T>(val date: LocalDate, val entries: List<T>)
