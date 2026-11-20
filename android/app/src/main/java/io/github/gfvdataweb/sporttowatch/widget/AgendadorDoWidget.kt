package io.github.gfvdataweb.sporttowatch.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.gfvdataweb.sporttowatch.SportToWatchApp
import java.util.concurrent.TimeUnit

/** Quando o widget busca dado novo (WorkManager: sobrevive a reinício e respeita a bateria). */
object AgendadorDoWidget {
    /** A cada 30 min (o mínimo do Android para trabalho periódico é 15). */
    const val INTERVALO_MIN = 30L
    private const val PERIODICA = "eventos-periodica"
    private const val AGORA = "eventos-agora"

    private val comInternet = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Liga a atualização periódica (KEEP: chamar de novo não reinicia o relógio). */
    fun agendarPeriodica(contexto: Context) {
        val periodica = PeriodicWorkRequestBuilder<AtualizadorWorker>(INTERVALO_MIN, TimeUnit.MINUTES)
            .setConstraints(comInternet)
            .build()
        WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP, periodica)
    }

    /** Último widget removido: para de buscar. */
    fun cancelarPeriodica(contexto: Context) {
        WorkManager.getInstance(contexto).cancelUniqueWork(PERIODICA)
    }

    /** Busca uma vez, assim que houver internet (widget novo, boot). */
    fun atualizarJa(contexto: Context) {
        val umaVez = OneTimeWorkRequestBuilder<AtualizadorWorker>().setConstraints(comInternet).build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(AGORA, ExistingWorkPolicy.KEEP, umaVez)
    }
}

/** Busca os eventos e redesenha o widget. Sem rede ou com erro, o widget segue com o dado salvo e mostra "sem conexão". */
class AtualizadorWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SportToWatchApp).container
        container.atualizador.atualizar()
        return Result.success()
    }
}
