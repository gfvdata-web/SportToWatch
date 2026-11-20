package io.github.gfvdataweb.sporttowatch.data

import io.github.gfvdataweb.sporttowatch.apoio.EVENTOS_JSON
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Parser do `/api/eventos` e as escolhas sobre a lista (JUnit puro). */
class EventosTest {

    private fun evento(
        id: String,
        inicio: String,
        esporte: Esporte = Esporte.CRUZEIRO,
        duracaoMin: Int = 120,
        aoVivo: Boolean = false,
    ) = Evento(id, esporte, "Título $id", "", "Liga", Instant.parse(inicio), duracaoMin, null, aoVivo)

    @Test
    fun leOContratoIgnorandoCamposNovos() {
        val lido = lerEventos(EVENTOS_JSON)

        assertEquals(Instant.parse("2099-01-01T12:00:00Z"), lido.agora)
        assertEquals(listOf("cru-1", "f1-1", "cs-1"), lido.eventos.map { it.id })
        val primeiro = lido.eventos[0]
        assertEquals(Esporte.CRUZEIRO, primeiro.esporte)
        assertEquals("Cruzeiro x Atlético-MG", primeiro.titulo)
        assertEquals("Brasileirão", primeiro.competicao)
        assertEquals("https://exemplo.test/jogo", primeiro.link)
        assertNull(lido.eventos[1].link)
        assertEquals(90, lido.eventos[2].duracaoMin)
        assertEquals(listOf(true, false), lido.fontes.map { it.ok })
        assertEquals("HTTP 500", lido.fontes[1].erro)
        assertNull(lido.fontes[1].atualizadoEm)
    }

    @Test
    fun esporteDesconhecidoViraOutroEEventoRuimEDescartado() {
        val lido = lerEventos(
            """{"eventos": [
              {"id": 7, "esporte": "tenis", "titulo": "Final", "inicio": "2099-01-01T10:00:00Z"},
              {"esporte": "f1", "inicio": "2099-01-01T10:00:00Z"},
              {"esporte": "f1", "titulo": "Sem data", "inicio": "ontem"},
              "lixo"
            ]}""",
        )

        assertEquals(1, lido.eventos.size)
        assertEquals(Esporte.OUTRO, lido.eventos[0].esporte)
        assertEquals("7", lido.eventos[0].id)
        assertEquals(DURACAO_PADRAO_MIN, lido.eventos[0].duracaoMin)
        assertEquals("", lido.eventos[0].competicao)
        assertTrue(lido.fontes.isEmpty())
    }

    @Test(expected = SerializationException::class)
    fun jsonQuebradoLancaExcecao() {
        lerEventos("<html>não é json</html>")
    }

    @Test(expected = SerializationException::class)
    fun semListaDeEventosLancaExcecao() {
        lerEventos("""{"agora": "2099-01-01T12:00:00Z"}""")
    }

    @Test
    fun aoVivoVemDoWorkerOuDoRelogioEEncerradoSome() {
        val agora = Instant.parse("2026-11-20T20:00:00Z")
        val comecou = evento("a", "2026-11-20T19:00:00Z")
        val marcado = evento("b", "2026-11-20T20:30:00Z", aoVivo = true)
        val futuro = evento("c", "2026-11-20T21:00:00Z")
        val acabou = evento("d", "2026-11-20T17:00:00Z", duracaoMin = 120)

        assertTrue(aoVivo(comecou, agora))
        assertTrue(aoVivo(marcado, agora))
        assertFalse(aoVivo(futuro, agora))
        assertTrue(encerrado(acabou, agora))
        assertFalse(aoVivo(acabou.copy(aoVivo = true), agora))
    }

    @Test
    fun proximosTiraEncerradosOrdenaECortaEmQuatro() {
        val agora = Instant.parse("2026-11-20T20:00:00Z")
        val lista = listOf(
            evento("e5", "2026-11-25T20:00:00Z"),
            evento("acabou", "2026-11-20T10:00:00Z"),
            evento("vivo", "2026-11-20T19:30:00Z"),
            evento("e2", "2026-11-21T20:00:00Z"),
            evento("e3", "2026-11-22T20:00:00Z"),
            evento("e4", "2026-11-23T20:00:00Z"),
        )

        assertEquals(listOf("vivo", "e2", "e3", "e4"), proximosEventos(lista, agora, 4).map { it.id })
        assertTrue(proximosEventos(emptyList(), agora, 4).isEmpty())
    }

    @Test
    fun agrupaPorEsporteNaOrdemDaTelaSemGruposVazios() {
        val agora = Instant.parse("2026-11-20T20:00:00Z")
        val lista = listOf(
            evento("cs2", "2026-11-22T20:00:00Z", Esporte.CS),
            evento("f1", "2026-11-21T20:00:00Z", Esporte.F1),
            evento("cru2", "2026-11-23T20:00:00Z"),
            evento("cru1", "2026-11-21T22:00:00Z"),
            evento("velho", "2026-11-01T22:00:00Z", Esporte.OUTRO),
        )

        val grupos = agruparPorEsporte(lista, agora)

        assertEquals(listOf(Esporte.CRUZEIRO, Esporte.F1, Esporte.CS), grupos.map { it.esporte })
        assertEquals(listOf("cru1", "cru2"), grupos[0].eventos.map { it.id })
    }

    @Test
    fun soAbreLinkHttps() {
        assertEquals("https://a.test/x", linkSeguro("https://a.test/x"))
        assertNull(linkSeguro("http://a.test/x"))
        assertNull(linkSeguro("intent://abrir"))
        assertNull(linkSeguro(null))
    }
}
