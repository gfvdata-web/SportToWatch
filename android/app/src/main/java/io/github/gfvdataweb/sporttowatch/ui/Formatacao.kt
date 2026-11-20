package io.github.gfvdataweb.sporttowatch.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Regras de exibição de data e hora, puras e sem Android: a tela e o widget usam as mesmas.
// Horário sempre em America/Sao_Paulo, qualquer que seja o fuso do celular.

val FUSO_SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** Dias da semana abreviados, da segunda (índice 0) ao domingo; fixos para não depender do idioma do celular. */
private val DIAS = arrayOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

/** "21:00". */
private fun hora(instante: Instant, zona: ZoneId): String {
    val local = instante.atZone(zona)
    return String.format(PT_BR, "%02d:%02d", local.hour, local.minute)
}

/** "sáb 22/11 · 21:00". */
fun formatarDataHora(instante: Instant, zona: ZoneId = FUSO_SAO_PAULO): String {
    val local = instante.atZone(zona)
    val dia = DIAS[local.dayOfWeek.value - 1]
    return String.format(PT_BR, "%s %02d/%02d · %s", dia, local.dayOfMonth, local.monthValue, hora(instante, zona))
}

/** "hoje 21:00", "amanhã 21:00" ou [formatarDataHora]; "hoje" é o dia de [agora] em [zona]. */
fun formatarQuando(instante: Instant, agora: Instant, zona: ZoneId = FUSO_SAO_PAULO): String {
    val diaDoEvento = instante.atZone(zona).toLocalDate()
    val hoje = agora.atZone(zona).toLocalDate()
    return when (diaDoEvento) {
        hoje -> "hoje ${hora(instante, zona)}"
        hoje.plusDays(1) -> "amanhã ${hora(instante, zona)}"
        else -> formatarDataHora(instante, zona)
    }
}
