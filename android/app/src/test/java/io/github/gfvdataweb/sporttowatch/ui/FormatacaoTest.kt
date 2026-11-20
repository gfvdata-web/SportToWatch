package io.github.gfvdataweb.sporttowatch.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Regras de data e hora do widget e da tela (JUnit puro). */
class FormatacaoTest {

    @Test
    fun dataHoraNoFusoDeSaoPauloComDiaAbreviado() {
        // 22/11/2026 00:00 UTC = sábado 21/11 21:00 em São Paulo (UTC-3).
        assertEquals("sáb 21/11 · 21:00", formatarDataHora(Instant.parse("2026-11-22T00:00:00Z")))
        assertEquals("seg 02/02 · 09:05", formatarDataHora(Instant.parse("2026-02-02T12:05:00Z")))
    }

    @Test
    fun ignoraOFusoDoCelular() {
        val instante = Instant.parse("2026-11-22T00:00:00Z")

        assertEquals("sáb 21/11 · 21:00", formatarDataHora(instante))
        assertEquals("dom 22/11 · 00:00", formatarDataHora(instante, ZoneId.of("UTC")))
    }

    @Test
    fun hojeEAmanhaPelaDataDeSaoPauloNaoPelaDeUtc() {
        // 15:00 UTC = 12:00 em São Paulo, sexta 20/11.
        val agora = Instant.parse("2026-11-20T15:00:00Z")

        assertEquals("hoje 21:00", formatarQuando(Instant.parse("2026-11-21T00:00:00Z"), agora)) // 21:00 de 20/11 em SP, já é 21/11 em UTC
        assertEquals("amanhã 10:30", formatarQuando(Instant.parse("2026-11-21T13:30:00Z"), agora))
        assertEquals("sáb 28/11 · 21:00", formatarQuando(Instant.parse("2026-11-29T00:00:00Z"), agora))
        assertEquals("qui 19/11 · 12:00", formatarQuando(Instant.parse("2026-11-19T15:00:00Z"), agora))
    }

    @Test
    fun viradaDoDiaUsaORelogioDeSaoPaulo() {
        // 02:00 UTC de 21/11 = 23:00 de 20/11 em SP: ainda é "hoje" lá.
        val agora = Instant.parse("2026-11-21T02:00:00Z")

        assertEquals("hoje 23:30", formatarQuando(Instant.parse("2026-11-21T02:30:00Z"), agora))
        assertEquals("amanhã 00:30", formatarQuando(Instant.parse("2026-11-21T03:30:00Z"), agora))
    }
}
