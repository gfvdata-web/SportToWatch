package io.github.gfvdataweb.sporttowatch.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.gfvdataweb.sporttowatch.MainActivity
import io.github.gfvdataweb.sporttowatch.R
import io.github.gfvdataweb.sporttowatch.data.Eventos
import io.github.gfvdataweb.sporttowatch.data.aoVivo
import io.github.gfvdataweb.sporttowatch.data.proximosEventos
import io.github.gfvdataweb.sporttowatch.ui.formatarQuando
import java.time.Instant

/** Quantos eventos cabem no widget (4x2). */
const val EVENTOS_NO_WIDGET = 4

/** Ids de uma das linhas fixas do layout `widget_eventos`. */
private class IdsDaLinha(
    val linha: Int,
    val barra: Int,
    val icone: Int,
    val quando: Int,
    val competicao: Int,
    val aoVivo: Int,
    val titulo: Int,
)

private val LINHAS = listOf(
    IdsDaLinha(R.id.linha_1, R.id.barra_1, R.id.icone_1, R.id.quando_1, R.id.competicao_1, R.id.ao_vivo_1, R.id.titulo_1),
    IdsDaLinha(R.id.linha_2, R.id.barra_2, R.id.icone_2, R.id.quando_2, R.id.competicao_2, R.id.ao_vivo_2, R.id.titulo_2),
    IdsDaLinha(R.id.linha_3, R.id.barra_3, R.id.icone_3, R.id.quando_3, R.id.competicao_3, R.id.ao_vivo_3, R.id.titulo_3),
    IdsDaLinha(R.id.linha_4, R.id.barra_4, R.id.icone_4, R.id.quando_4, R.id.competicao_4, R.id.ao_vivo_4, R.id.titulo_4),
)

/**
 * Monta o que o widget mostra: os [EVENTOS_NO_WIDGET] próximos eventos (cor e ícone do esporte,
 * "hoje 21:00" / "sáb 22/11 · 21:00", título e competição, "AO VIVO" quando em andamento).
 * [eventos] nulo = nunca baixou; [semConexao] liga o rótulo "sem conexão" sem apagar o dado salvo.
 */
fun montarVista(contexto: Context, eventos: Eventos?, semConexao: Boolean, agora: Instant): RemoteViews {
    val vista = RemoteViews(contexto.packageName, R.layout.widget_eventos)
    val proximos = proximosEventos(eventos?.eventos.orEmpty(), agora, EVENTOS_NO_WIDGET)
    LINHAS.forEachIndexed { indice, ids ->
        val evento = proximos.getOrNull(indice)
        if (evento == null) {
            vista.setViewVisibility(ids.linha, View.GONE)
            return@forEachIndexed
        }
        vista.setViewVisibility(ids.linha, View.VISIBLE)
        vista.setInt(ids.barra, "setBackgroundColor", evento.esporte.corArgb)
        vista.setTextViewText(ids.icone, evento.esporte.icone)
        vista.setTextViewText(ids.quando, formatarQuando(evento.inicio, agora))
        vista.setTextColor(ids.quando, evento.esporte.corArgb)
        vista.setTextViewText(ids.competicao, evento.competicao)
        vista.setTextViewText(ids.titulo, evento.titulo)
        vista.setViewVisibility(ids.aoVivo, if (aoVivo(evento, agora)) View.VISIBLE else View.GONE)
    }
    vista.setViewVisibility(R.id.widget_vazio, if (proximos.isEmpty()) View.VISIBLE else View.GONE)
    vista.setViewVisibility(R.id.widget_status, if (semConexao) View.VISIBLE else View.GONE)
    vista.setOnClickPendingIntent(R.id.widget_raiz, intencaoDeAbrirOApp(contexto))
    return vista
}

private fun intencaoDeAbrirOApp(contexto: Context): PendingIntent {
    val intent = Intent(contexto, MainActivity::class.java)
    return PendingIntent.getActivity(contexto, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
