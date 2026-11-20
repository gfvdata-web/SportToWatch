package io.github.gfvdataweb.sporttowatch.data

import io.github.gfvdataweb.sporttowatch.apoio.ArmazemEmMemoria
import io.github.gfvdataweb.sporttowatch.apoio.EVENTOS_JSON
import io.github.gfvdataweb.sporttowatch.apoio.WorkerLocal
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cliente HTTP e Atualizador contra um Worker local (MockWebServer). */
class ClienteEventosTest {

    private val worker = WorkerLocal()
    private val cliente = ClienteEventos(OkHttpClient(), worker.urlDosEventos)

    @After
    fun encerra() {
        worker.fechar()
    }

    @Test
    fun baixaEDevolveOTextoLegivel() = runTest {
        val texto = cliente.buscar()

        assertEquals(EVENTOS_JSON, texto)
        assertEquals(3, lerEventos(texto!!).eventos.size)
    }

    @Test
    fun erroDeHttpOuJsonQuebradoViraNulo() = runTest {
        worker.foraDoAr = true
        assertNull(cliente.buscar())
        worker.foraDoAr = false
        worker.eventos = "<html>não é json</html>"
        assertNull(cliente.buscar())
        worker.eventos = """{"sem": "lista"}"""
        assertNull(cliente.buscar())
    }

    @Test
    fun atualizadorGuardaOTextoEAvisaQuemDesenha() = runTest {
        val armazem = ArmazemEmMemoria(semConexao = true)
        var avisos = 0
        val atualizador = Atualizador(cliente, armazem) { avisos++ }

        assertTrue(atualizador.atualizar())

        assertEquals(EVENTOS_JSON, armazem.texto)
        assertFalse(armazem.semConexao())
        assertEquals(1, avisos)
    }

    @Test
    fun atualizadorSemRedeMantemODadoSalvoEMarcaSemConexao() = runTest {
        val armazem = ArmazemEmMemoria()
        var avisos = 0
        val atualizador = Atualizador(cliente, armazem) { avisos++ }
        atualizador.atualizar()
        val salvo = armazem.ler()
        assertNotNull(salvo)

        worker.foraDoAr = true

        assertFalse(atualizador.atualizar())
        assertEquals(salvo, armazem.ler())
        assertTrue(armazem.semConexao())
        assertEquals(2, avisos)
    }
}
