package app.catatuang

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File

fun ComposeContentTestRule.waitText(text: String, substring: Boolean = false) =
    waitUntil(10_000) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

/** Simpan screenshot root (atau window sheet yang memuat [containing]) ke build/screenshots. */
fun ComposeContentTestRule.shot(name: String, containing: String? = null) {
    waitForIdle()
    val dir = File(System.getProperty("robolectric.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    val matcher = if (containing != null) isRoot() and hasAnyDescendant(hasText(containing, substring = true)) else isRoot()
    val roots = onAllNodes(matcher).fetchSemanticsNodes()
    val node: SemanticsNodeInteraction = onAllNodes(matcher)[roots.lastIndex]
    val bmp = node.captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
