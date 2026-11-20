package io.github.gfvdataweb.sporttowatch.data

import io.github.gfvdataweb.sporttowatch.apoio.WorkerLocal
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aviso de nova versão contra um `app/versao.json` local (o do Worker). */
class VerificadorDeAtualizacaoTest {

    private val worker = WorkerLocal()
    private val verificador = VerificadorDeAtualizacao(OkHttpClient(), worker.urlDaVersao)

    @After
    fun encerra() {
        worker.fechar()
    }

    private fun versao(apk: String) = """
        {"versao": "0.2.0", "build": 20, "apk": "$apk", "notas": "Notas da versão.",
         "publicado_em": "2026-11-07T12:00:00Z", "campo_novo": 1}
    """.trimIndent()

    @Test
    fun leVersaoEBuildDoNomeDoApkEMontaOEnderecoAoLadoDoVersaoJson() = runTest {
        worker.versao = versao("sporttowatch-v0.2.0-build20.apk")

        val publicada = verificador.maisRecente()

        assertEquals("0.2.0", publicada?.versao)
        assertEquals(20, publicada?.build)
        assertEquals(worker.urlDaVersao.resolve("sporttowatch-v0.2.0-build20.apk").toString(), publicada?.urlDoApk)
        assertEquals("Notas da versão.", publicada?.notas)
    }

    @Test
    fun apkForaDoPadraoOuLinkDeForaNaoViramAviso() = runTest {
        worker.versao = versao("nome-fora-do-padrao.apk")
        assertNull(verificador.maisRecente())
        worker.versao = versao("https://outro.site/sporttowatch-v9.9.9-build99.apk")
        assertNull(verificador.maisRecente())
    }

    @Test
    fun falhaNaConsultaNaoAtrapalha() = runTest {
        assertNull(verificador.maisRecente()) // 404: nada publicado ainda
        worker.versao = "isto não é json"
        assertNull(verificador.maisRecente())
        worker.foraDoAr = true
        assertNull(verificador.maisRecente())
    }

    @Test
    fun soAvisaQuandoOBuildPublicadoEMaior() {
        val publicada = VersaoPublicada("0.2.0", build = 20, urlDoApk = "", notas = "")
        assertTrue(haAtualizacao(publicada, buildInstalado = 12))
        assertFalse(haAtualizacao(publicada, buildInstalado = 20))
        assertFalse(haAtualizacao(publicada, buildInstalado = 31))
        assertFalse(haAtualizacao(null, buildInstalado = 1))
    }
}
