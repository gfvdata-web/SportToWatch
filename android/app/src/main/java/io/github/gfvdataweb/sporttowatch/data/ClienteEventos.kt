package io.github.gfvdataweb.sporttowatch.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Baixa o `/api/eventos` do Worker e devolve o texto bruto (para guardar), só se ele for legível
 * por [lerEventos]. Qualquer falha (rede, HTTP de erro, JSON que não bate) vira null: quem chama
 * mantém o último dado salvo.
 */
class ClienteEventos(
    private val cliente: OkHttpClient,
    private val urlDosEventos: HttpUrl,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun buscar(): String? = withContext(io) {
        try {
            val pedido = Request.Builder().url(urlDosEventos).cacheControl(CacheControl.FORCE_NETWORK).build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@use null
                val texto = resposta.body.string()
                lerEventos(texto)
                texto
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
