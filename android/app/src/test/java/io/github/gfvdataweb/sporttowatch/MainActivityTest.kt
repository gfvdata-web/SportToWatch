package io.github.gfvdataweb.sporttowatch

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.github.gfvdataweb.sporttowatch.apoio.SportToWatchAppDeTeste
import io.github.gfvdataweb.sporttowatch.apoio.WorkerDeTeste
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Abre o app de verdade (manifest, tema, Activity, ViewModel, rede) num Android simulado,
 * lendo de um Worker local com eventos de 2099. Se o app fecharia sozinho ao abrir no celular,
 * este teste falha no CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = SportToWatchAppDeTeste::class, qualifiers = "w411dp-h2000dp")
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun restaura() {
        WorkerDeTeste.worker.foraDoAr = false
    }

    private fun esperarTexto(texto: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun abreEMostraOsEventosAgrupadosPorEsporte() {
        esperarTexto("Cruzeiro x Atlético-MG")
        esperarTexto("⚽ Cruzeiro")
        esperarTexto("🏎️ Fórmula 1")
        esperarTexto("🎯 CS2")
        esperarTexto("GP de Abu Dhabi")
        esperarTexto("FURIA x NAVI")
    }

    @Test
    fun mostraAVersaoInstaladaEAInstrucaoDoWidget() {
        esperarTexto("Versão ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
        esperarTexto("Widget na tela inicial")
    }
}
