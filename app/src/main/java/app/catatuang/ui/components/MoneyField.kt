package app.catatuang.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

/** Menampilkan "1500000" sebagai "1.500.000" tanpa mengubah nilai yang diketik. */
private object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = StringBuilder()
        raw.forEachIndexed { i, ch ->
            if (i > 0 && (raw.length - i) % 3 == 0) out.append('.')
            out.append(ch)
        }
        val shown = out.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var digits = 0
                shown.forEachIndexed { i, ch ->
                    if (digits == offset) return i
                    if (ch != '.') digits++
                }
                return shown.length
            }

            override fun transformedToOriginal(offset: Int): Int =
                shown.take(offset.coerceIn(0, shown.length)).count { it != '.' }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** Isian nominal rupiah (angka saja, Long). */
@Composable
fun MoneyField(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
    val c = CatatTheme.colors
    OutlinedTextField(
        value = if (value == 0L) "" else value.toString(),
        onValueChange = { s ->
            val clean = s.filter { it.isDigit() }.trimStart('0').take(12)
            onChange(clean.toLongOrNull() ?: 0)
        },
        label = { Text(label) },
        prefix = { Text("Rp ", style = CatatType.body, color = c.textSecondary) },
        placeholder = { Text("0") },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = CatatType.money.copy(fontSize = CatatType.body.fontSize, color = c.textPrimary),
        shape = CatatShapes.chip,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.divider,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
