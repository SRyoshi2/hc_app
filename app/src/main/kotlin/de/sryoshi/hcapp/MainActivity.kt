package de.sryoshi.hcapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import de.sryoshi.hcapp.ui.HcApp
import de.sryoshi.hcapp.ui.welt
import de.sryoshi.hcapp.ui.theme.HcTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Die Farbwelt folgt dem Bildschirm: Küche (Einkaufsliste, Rezepte) im Grün, sonst HomeCenter-Rot.
            val welt = viewModel.screen.welt()
            HcTheme(welt) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Zurück geht von jedem Bildschirm außer der Instanzliste (und dem ersten Einrichten) einen Schritt.
                    BackHandler(enabled = viewModel.screen !is Screen.Instances &&
                        !(viewModel.screen is Screen.AddInstance && viewModel.instances.isEmpty())) {
                        viewModel.back()
                    }
                    HcApp(viewModel)
                }
            }
        }
    }
}
