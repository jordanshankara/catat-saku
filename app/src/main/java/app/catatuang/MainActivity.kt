package app.catatuang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.catatuang.ui.nav.CatatNavHost
import app.catatuang.ui.theme.CatatUangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatatUangTheme {
                CatatNavHost()
            }
        }
    }
}
