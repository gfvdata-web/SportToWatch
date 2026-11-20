package io.github.gfvdataweb.sporttowatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gfvdataweb.sporttowatch.ui.SportToWatchTheme
import io.github.gfvdataweb.sporttowatch.ui.TelaPrincipal
import io.github.gfvdataweb.sporttowatch.ui.TelaViewModel
import io.github.gfvdataweb.sporttowatch.widget.fixarWidget
import io.github.gfvdataweb.sporttowatch.widget.podeFixarWidget

/** Única Activity do app: uma tela Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SportToWatchApp).container
        val podeFixar = podeFixarWidget(this)
        setContent {
            SportToWatchTheme {
                val modelo: TelaViewModel = viewModel(factory = TelaViewModel.fabrica(container, BuildConfig.VERSION_CODE))
                val estado by modelo.estado.collectAsStateWithLifecycle()
                val links = LocalUriHandler.current
                TelaPrincipal(
                    estado = estado,
                    versao = BuildConfig.VERSION_NAME,
                    build = BuildConfig.VERSION_CODE,
                    podeFixarWidget = podeFixar,
                    aoAtualizar = modelo::atualizar,
                    aoFixarWidget = { fixarWidget(this) },
                    aoBaixar = { url -> links.openUri(url) },
                    aoAbrirLink = { url -> links.openUri(url) },
                )
            }
        }
    }
}
