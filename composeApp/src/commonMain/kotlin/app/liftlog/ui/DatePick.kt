package app.liftlog.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.dLong
import app.liftlog.logic.ldt
import app.liftlog.logic.tShort
import app.liftlog.logic.tz
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** A tappable box that looks like a text field and opens a picker. */
@Composable
private fun PickBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(modifier) {
        Mu(label, Modifier.padding(bottom = 6.dp), size = 14.sp)
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, p.ln, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp)) {
            Txt(value, size = 16.sp, maxLines = 1)
        }
    }
}

/** Material pickers in the app's own colours. */
@Composable
private fun PickerTheme(content: @Composable () -> Unit) {
    val p = LocalPal.current
    val base = if (p.bg.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
    MaterialTheme(base.copy(primary = p.ac, onPrimary = androidx.compose.ui.graphics.Color.White, surface = p.s1, onSurface = p.ink,
        surfaceContainerHigh = p.s1, surfaceContainerHighest = p.s2, onSurfaceVariant = p.mu, outline = p.ln, secondaryContainer = p.s2), content = content)
}

/**
 * Date (and optionally time) chosen with pickers instead of typed text. [ms] is local time; the date picker works in
 * UTC days, so the chosen day is mapped back onto the same clock time here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeFields(ms: Long, withTime: Boolean, onChange: (Long) -> Unit, modifier: Modifier = Modifier, dateLabel: String = "Date") {
    var dlg by remember { mutableStateOf<String?>(null) }
    val d = ldt(ms)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickBox(dateLabel, dLong(ms), Modifier.weight(1.4f)) { dlg = "d" }
        if (withTime) PickBox("Time", tShort(ms), Modifier.weight(1f)) { dlg = "t" }
    }
    if (dlg == "d") PickerTheme {
        val state = rememberDatePickerState(initialSelectedDateMillis = d.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(onDismissRequest = { dlg = null },
            confirmButton = { TextButton({
                state.selectedDateMillis?.let { sel ->
                    val day = Instant.fromEpochMilliseconds(sel).toLocalDateTime(TimeZone.UTC).date
                    onChange(LocalDateTime(day, d.time).toInstant(tz).toEpochMilliseconds())
                }
                dlg = null
            }) { Text("OK") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } }) { DatePicker(state) }
    }
    if (dlg == "t") PickerTheme {
        val state = rememberTimePickerState(d.hour, d.minute, is24Hour = false)
        AlertDialog(onDismissRequest = { dlg = null },
            confirmButton = { TextButton({ onChange(LocalDateTime(d.date, LocalTime(state.hour, state.minute)).toInstant(tz).toEpochMilliseconds()); dlg = null }) { Text("OK") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            text = { TimePicker(state) })
    }
}
