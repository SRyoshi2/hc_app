package de.sryoshi.hcapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import de.sryoshi.hcapp.ui.HcApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
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
