package io.github.gfvdataweb.sporttowatch.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/** Esporte de um evento, com o ícone e a cor usados na tela e no widget. Código desconhecido vira [OUTRO]. */
enum class Esporte(val codigo: String, val icone: String, val corArgb: Int) {
    CRUZEIRO("cruzeiro", "⚽", 0xFF2563EB.toInt()),
    F1("f1", "🏎️", 0xFFE10600.toInt()),
    CS("cs", "🎯", 0xFFF97316.toInt()),
    OUTRO("", "🏆", 0xFF64748B.toInt()),
    ;

    companion object {
        fun deCodigo(codigo: String?): Esporte = entries.firstOrNull { it != OUTRO && it.codigo == codigo } ?: OUTRO
    }
}

/** Duração assumida quando o Worker não manda `duracao_min` (ou manda zero). */
const val DURACAO_PADRAO_MIN = 120

/** Um evento do `/api/eventos` (horários em UTC; quem desenha converte para America/Sao_Paulo). */
data class Evento(
    val id: String,
    val esporte: Esporte,
    val titulo: String,
    val detalhe: String,
    val competicao: String,
    val inicio: Instant,
    val duracaoMin: Int,
    val link: String?,
    val aoVivo: Boolean,
)

/** Estado de uma fonte de dados do Worker (para saber se algo está desatualizado). */
data class Fonte(val nome: String, val atualizadoEm: Instant?, val ok: Boolean, val erro: String?)

/** O `/api/eventos` inteiro: eventos já ordenados pelo Worker, mais o estado das fontes. */
data class Eventos(val agora: Instant?, val eventos: List<Evento>, val fontes: List<Fonte>)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Lê o JSON do `GET /api/eventos` (contrato no CLAUDE.md do repo). Leitor tolerante: campos
 * desconhecidos são ignorados, evento sem `titulo` ou com `inicio` inválido é descartado
 * (os demais ficam), `link` nulo ou ausente é válido. JSON inválido, ou sem a lista `eventos`,
 * lança [SerializationException] (quem chama mantém o último dado salvo).
 */
fun lerEventos(texto: String): Eventos {
    val raiz = json.parseToJsonElement(texto) as? JsonObject ?: throw SerializationException("resposta não é um objeto")
    val lista = raiz["eventos"] as? JsonArray ?: throw SerializationException("sem a lista eventos")
    val fontes = (raiz["fontes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::lerFonte) }
    return Eventos(
        agora = instante((raiz["agora"] as? JsonPrimitive)?.textoOuNulo()),
        eventos = lista.mapNotNull { (it as? JsonObject)?.let(::lerEvento) },
        fontes = fontes,
    )
}

private fun lerEvento(o: JsonObject): Evento? {
    val titulo = o.texto("titulo")?.takeIf { it.isNotBlank() } ?: return null
    val inicio = instante(o.texto("inicio")) ?: return null
    val duracao = o.inteiro("duracao_min")?.takeIf { it > 0 } ?: DURACAO_PADRAO_MIN
    return Evento(
        id = o.texto("id") ?: "${o.texto("esporte")}-$inicio-$titulo",
        esporte = Esporte.deCodigo(o.texto("esporte")),
        titulo = titulo,
        detalhe = o.texto("detalhe").orEmpty(),
        competicao = o.texto("competicao").orEmpty(),
        inicio = inicio,
        duracaoMin = duracao,
        link = o.texto("link")?.takeIf { it.isNotBlank() },
        aoVivo = (o["ao_vivo"] as? JsonPrimitive)?.booleanOrNull ?: false,
    )
}

private fun lerFonte(o: JsonObject): Fonte? {
    val nome = o.texto("nome") ?: return null
    return Fonte(
        nome = nome,
        atualizadoEm = instante(o.texto("atualizado_em")),
        ok = (o["ok"] as? JsonPrimitive)?.booleanOrNull ?: false,
        erro = o.texto("erro"),
    )
}

private fun JsonPrimitive.textoOuNulo(): String? = if (this is JsonNull) null else content

private fun JsonObject.texto(chave: String): String? = (this[chave] as? JsonPrimitive)?.textoOuNulo()

private fun JsonObject.inteiro(chave: String): Int? = (this[chave] as? JsonPrimitive)?.intOrNull

private fun instante(texto: String?): Instant? =
    try {
        texto?.let { Instant.parse(it) }
    } catch (e: DateTimeParseException) {
        null
    }
