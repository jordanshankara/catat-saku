package app.catatuang.feature.fixed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.catatuang.data.AppState
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.input.appendDigits
import app.catatuang.feature.input.backspace
import app.catatuang.ui.components.BigAmount
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Numpad
import app.catatuang.ui.components.PrimaryButton
import app.catatuang.ui.format.rp
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

/** R-41: "AI jatuh tempo. Bayar berapa?" — nominal estimasi terisi; konfirmasi → LUNAS. */
@Composable
fun FixedPayContent(due: FixedDue, ready: AppState.Ready, vm: LedgerViewModel, onDone: () -> Unit) {
    val c = CatatTheme.colors
    var amount by remember(due.categoryId) { mutableLongStateOf(due.estimate) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(due.key, 44)
            Column(Modifier.weight(1f)) {
                Text("${due.name} jatuh tempo. Bayar berapa?", style = CatatType.cardTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
                Text("Estimasi ${rp(due.estimate)}", style = CatatType.bodySmall, color = c.textSecondary)
            }
        }
        BigAmount(amount)
        if (amount > 0) fixedDiffText(due.estimate, amount).let { (t, tone) -> NoticeBox(t, tone) }
        Numpad(onDigits = { amount = appendDigits(amount, it) }, onBackspace = { amount = backspace(amount) })
        PrimaryButton(if (amount > 0) "Bayar · ${rp(amount)}" else "Bayar", enabled = amount > 0, onClick = {
            val (_, tone) = fixedDiffText(due.estimate, amount)
            vm.payFixed(ready.ledger.currentMonth, due.categoryId, amount, due.estimate, "${due.name} LUNAS · ${rp(amount)}", tone)
            onDone()
        })
        Spacer(Modifier.height(4.dp))
    }
}
