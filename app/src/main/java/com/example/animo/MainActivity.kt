package com.example.animo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.animo.ui.AnimoApp
import com.example.animo.ui.AppViewModel
import com.example.animo.ui.theme.AnimoTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** FragmentActivity porque BiometricPrompt la necesita para mostrar el diálogo de huella. */
class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // FLAG_SECURE oculta el contenido en "recientes" y bloquea capturas de pantalla.
        lifecycleScope.launch {
            vm.settings.map { it.secureScreen }.distinctUntilChanged().collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            AnimoTheme {
                AnimoApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        // Rotar la pantalla no cuenta como salir de la app.
        if (!isChangingConfigurations) vm.onAppBackground()
    }
}
