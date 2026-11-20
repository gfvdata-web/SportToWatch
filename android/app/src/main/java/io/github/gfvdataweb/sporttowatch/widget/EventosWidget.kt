package io.github.gfvdataweb.sporttowatch.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import io.github.gfvdataweb.sporttowatch.SportToWatchApp
import io.github.gfvdataweb.sporttowatch.data.Armazem
import java.time.Instant

/**
 * Widget da tela inicial (RemoteViews, sem Glance). Desenha sempre o último dado guardado;
 * quem busca é o [AtualizadorWorker]: ao adicionar o widget, ao reiniciar o celular (o sistema
 * chama [onUpdate]) e a cada 30 min. O toque abre o app.
 */
class EventosWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val armazem = (context.applicationContext as SportToWatchApp).container.armazem
        appWidgetManager.updateAppWidget(appWidgetIds, montarVista(context, armazem.ler(), armazem.semConexao(), Instant.now()))
        AgendadorDoWidget.agendarPeriodica(context)
        AgendadorDoWidget.atualizarJa(context)
    }

    override fun onEnabled(context: Context) {
        AgendadorDoWidget.agendarPeriodica(context)
    }

    override fun onDisabled(context: Context) {
        AgendadorDoWidget.cancelarPeriodica(context)
    }

    companion object {
        /** Redesenha todos os widgets colocados na tela com o que há no [armazem]. */
        fun redesenhar(contexto: Context, armazem: Armazem) {
            val gerenciador = AppWidgetManager.getInstance(contexto)
            val ids = gerenciador.getAppWidgetIds(ComponentName(contexto, EventosWidget::class.java))
            if (ids.isEmpty()) return
            gerenciador.updateAppWidget(ids, montarVista(contexto, armazem.ler(), armazem.semConexao(), Instant.now()))
        }
    }
}

/** O launcher aceita fixar widget por pedido do app? (Android 8+, mas depende do launcher.) */
fun podeFixarWidget(contexto: Context): Boolean = AppWidgetManager.getInstance(contexto).isRequestPinAppWidgetSupported

/** Pede ao launcher para colocar o widget na tela inicial (ele mostra a confirmação). */
fun fixarWidget(contexto: Context) {
    AppWidgetManager.getInstance(contexto).requestPinAppWidget(ComponentName(contexto, EventosWidget::class.java), null, null)
}
