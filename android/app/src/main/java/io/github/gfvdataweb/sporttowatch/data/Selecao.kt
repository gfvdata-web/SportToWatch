package io.github.gfvdataweb.sporttowatch.data

import java.time.Instant

// Escolhas sobre a lista de eventos, puras e sem Android: a tela e o widget usam as mesmas.
// O relógio entra como parâmetro: o dado salvo pode estar velho (sem conexão), então o que já
// acabou some e o que já começou vira "ao vivo" mesmo sem resposta nova do Worker.

/** Quando o evento termina (início + duração). */
fun fimDoEvento(evento: Evento): Instant = evento.inicio.plusSeconds(evento.duracaoMin * 60L)

fun encerrado(evento: Evento, agora: Instant): Boolean = !fimDoEvento(evento).isAfter(agora)

/** Em andamento: o Worker marcou `ao_vivo` ou o horário de início já passou (e ainda não terminou). */
fun aoVivo(evento: Evento, agora: Instant): Boolean =
    !encerrado(evento, agora) && (evento.aoVivo || !evento.inicio.isAfter(agora))

/** Os [limite] próximos eventos (em andamento primeiro, depois por início), sem os encerrados. */
fun proximosEventos(eventos: List<Evento>, agora: Instant, limite: Int): List<Evento> =
    eventos.filterNot { encerrado(it, agora) }.sortedBy { it.inicio }.take(limite)

/** Eventos de um esporte, na ordem da tela. */
data class GrupoDeEsporte(val esporte: Esporte, val eventos: List<Evento>)

/** Agrupa por esporte (Cruzeiro, F1, CS2, outros), cada grupo por início; esporte sem evento não aparece. */
fun agruparPorEsporte(eventos: List<Evento>, agora: Instant): List<GrupoDeEsporte> {
    val vivos = eventos.filterNot { encerrado(it, agora) }.sortedBy { it.inicio }
    return Esporte.entries.mapNotNull { esporte ->
        vivos.filter { it.esporte == esporte }.takeIf { it.isNotEmpty() }?.let { GrupoDeEsporte(esporte, it) }
    }
}

/** Só abre link HTTPS vindo do Worker (o app nunca segue outro esquema). */
fun linkSeguro(link: String?): String? = link?.takeIf { it.startsWith("https://") }
