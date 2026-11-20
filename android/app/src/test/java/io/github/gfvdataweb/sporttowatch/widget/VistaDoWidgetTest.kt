package io.github.gfvdataweb.sporttowatch.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.gfvdataweb.sporttowatch.R
import io.github.gfvdataweb.sporttowatch.apoio.SportToWatchAppDeTeste
import io.github.gfvdataweb.sporttowatch.data.Esporte
import io.github.gfvdataweb.sporttowatch.data.Evento
import io.github.gfvdataweb.sporttowatch.data.Eventos
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Infla o RemoteViews do widget num Android simulado e confere os textos desenhados. */
@RunWith(RobolectricTestRunner::class)
@Config(application = SportToWatchAppDeTeste::class)
class VistaDoWidgetTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    // 12:00 em São Paulo, sexta 20/11/2026.
    private val agora = Instant.parse("2026-11-20T15:00:00Z")

    private fun desenhar(vista: RemoteViews): View = vista.apply(contexto, FrameLayout(contexto))

    private fun texto(raiz: View, id: Int): String = raiz.findViewById<TextView>(id).text.toString()

    private fun visivel(raiz: View, id: Int): Boolean = raiz.findViewById<View>(id).visibility == View.VISIBLE

    private fun evento(id: String, esporte: Esporte, titulo: String, inicio: String, aoVivo: Boolean = false) =
        Evento(id, esporte, titulo, "", "Liga $id", Instant.parse(inicio), 120, null, aoVivo)

    private fun eventos(vararg lista: Evento) = Eventos(agora, lista.toList(), emptyList())

    @Test
    fun mostraQuandoTituloECompeticaoDosQuatroProximos() {
        val lista = eventos(
            evento("1", Esporte.CRUZEIRO, "Cruzeiro x Atlético", "2026-11-21T00:00:00Z"),
            evento("2", Esporte.F1, "GP de Las Vegas", "2026-11-21T13:30:00Z"),
            evento("3", Esporte.CS, "FURIA x NAVI", "2026-11-29T00:00:00Z"),
            evento("4", Esporte.CRUZEIRO, "Grêmio x Cruzeiro", "2026-12-02T00:00:00Z"),
            evento("5", Esporte.CRUZEIRO, "Quinto", "2026-12-03T00:00:00Z"),
        )

        val raiz = desenhar(montarVista(contexto, lista, semConexao = false, agora = agora))

        assertEquals("hoje 21:00", texto(raiz, R.id.quando_1))
        assertEquals("Cruzeiro x Atlético", texto(raiz, R.id.titulo_1))
        assertEquals("Liga 1", texto(raiz, R.id.competicao_1))
        assertEquals("⚽", texto(raiz, R.id.icone_1))
        assertEquals("amanhã 10:30", texto(raiz, R.id.quando_2))
        assertEquals("sáb 28/11 · 21:00", texto(raiz, R.id.quando_3))
        assertEquals("Grêmio x Cruzeiro", texto(raiz, R.id.titulo_4))
        assertEquals(false, visivel(raiz, R.id.widget_vazio))
        assertEquals(false, visivel(raiz, R.id.widget_status))
    }

    @Test
    fun marcaAoVivoEEscondeLinhasSemEvento() {
        val lista = eventos(
            evento("1", Esporte.F1, "Treino", "2026-11-20T14:00:00Z"),
            evento("2", Esporte.CS, "Final", "2026-11-22T00:00:00Z"),
        )

        val raiz = desenhar(montarVista(contexto, lista, semConexao = false, agora = agora))

        assertEquals(true, visivel(raiz, R.id.ao_vivo_1))
        assertEquals(false, visivel(raiz, R.id.ao_vivo_2))
        assertEquals(true, visivel(raiz, R.id.linha_2))
        assertEquals(false, visivel(raiz, R.id.linha_3))
        assertEquals(false, visivel(raiz, R.id.linha_4))
    }

    @Test
    fun semConexaoMantemOUltimoDadoEMostraOAviso() {
        val lista = eventos(evento("1", Esporte.CS, "Final", "2026-11-22T00:00:00Z"))

        val raiz = desenhar(montarVista(contexto, lista, semConexao = true, agora = agora))

        assertEquals("Final", texto(raiz, R.id.titulo_1))
        assertEquals(true, visivel(raiz, R.id.widget_status))
        assertEquals("sem conexão", texto(raiz, R.id.widget_status))
    }

    @Test
    fun semDadoOuSemEventoMostraMensagemVazia() {
        val nuncaBaixou = desenhar(montarVista(contexto, null, semConexao = true, agora = agora))
        assertEquals(true, visivel(nuncaBaixou, R.id.widget_vazio))
        assertEquals(false, visivel(nuncaBaixou, R.id.linha_1))

        val soEncerrados = eventos(evento("1", Esporte.F1, "Velho", "2026-11-01T10:00:00Z"))
        val raiz = desenhar(montarVista(contexto, soEncerrados, semConexao = false, agora = agora))
        assertEquals(true, visivel(raiz, R.id.widget_vazio))
    }
}
