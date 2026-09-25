package app.catatuang.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sumber "hari ini" untuk ledger: tanggal perangkat (zona waktu perangkat), diperbarui saat lewat
 * tengah malam (R-21). [override] dipakai Mode Uji Tanggal (8.11) di Fase 4.
 */
class AppClock(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    val override = MutableStateFlow<LocalDate?>(null)

    private val deviceToday: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(zone())
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
        }
    }

    val today: Flow<LocalDate> = combine(deviceToday, override) { device, test -> test ?: device }.distinctUntilChanged()

    fun now(): LocalDate = override.value ?: LocalDate.now(zone())
}
