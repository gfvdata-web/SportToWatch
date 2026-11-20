package io.github.gfvdataweb.sporttowatch

import android.app.Application
import android.content.Context
import io.github.gfvdataweb.sporttowatch.data.Armazem
import io.github.gfvdataweb.sporttowatch.data.ArmazemPreferencias
import io.github.gfvdataweb.sporttowatch.data.Atualizador
import io.github.gfvdataweb.sporttowatch.data.ClienteEventos
import io.github.gfvdataweb.sporttowatch.data.VerificadorDeAtualizacao
import io.github.gfvdataweb.sporttowatch.widget.EventosWidget
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Endereços externos que o app conhece (só leitura, nada de token). */
object Enderecos {
    const val EVENTOS = "https://esportes.gfvdata.com/api/eventos"

    /** Versão oficial mais recente do app (aviso de nova versão); o APK fica ao lado. */
    const val VERSAO = "https://esportes.gfvdata.com/app/versao.json"
}

/**
 * Contêiner de dependências manual (sem Hilt): cria cada peça uma vez e entrega para a tela,
 * o widget e o worker. Nos testes, o app de teste monta as peças à mão apontando para um
 * servidor local.
 */
class AppContainer(
    val armazem: Armazem,
    val atualizador: Atualizador,
    val verificador: VerificadorDeAtualizacao,
) {
    companion object {
        fun padrao(contexto: Context): AppContainer {
            val cliente = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return montar(contexto, cliente, Enderecos.EVENTOS.toHttpUrl(), Enderecos.VERSAO.toHttpUrl())
        }

        fun montar(contexto: Context, cliente: OkHttpClient, eventos: HttpUrl, versao: HttpUrl): AppContainer {
            val app = contexto.applicationContext
            val armazem = ArmazemPreferencias(app)
            val atualizador = Atualizador(ClienteEventos(cliente, eventos), armazem) { EventosWidget.redesenhar(app, armazem) }
            return AppContainer(armazem, atualizador, VerificadorDeAtualizacao(cliente, versao))
        }
    }
}

/** Application: guarda o [AppContainer]. Os testes trocam por uma subclasse (`@Config(application = ...)`). */
open class SportToWatchApp : Application() {
    open val container: AppContainer by lazy { AppContainer.padrao(this) }
}
