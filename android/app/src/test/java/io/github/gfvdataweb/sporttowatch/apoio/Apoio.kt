package io.github.gfvdataweb.sporttowatch.apoio

import io.github.gfvdataweb.sporttowatch.AppContainer
import io.github.gfvdataweb.sporttowatch.SportToWatchApp
import io.github.gfvdataweb.sporttowatch.data.Armazem
import io.github.gfvdataweb.sporttowatch.data.Eventos
import io.github.gfvdataweb.sporttowatch.data.lerEventos
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

// Peças compartilhadas pelos testes. Nada aqui acessa a internet.

/**
 * Resposta de exemplo do `/api/eventos` (formato do contrato, com um campo novo e um esporte
 * desconhecido para provar a leitura aditiva). As datas são de 2099: nunca "encerradas" no relógio real.
 */
val EVENTOS_JSON = """
    {
      "agora": "2099-01-01T12:00:00.000Z",
      "campo_novo": {"qualquer": "coisa"},
      "eventos": [
        {"id": "cru-1", "esporte": "cruzeiro", "titulo": "Cruzeiro x Atlético-MG", "detalhe": "Mineirão",
         "competicao": "Brasileirão", "inicio": "2099-01-02T00:00:00.000Z", "duracao_min": 120,
         "link": "https://exemplo.test/jogo", "ao_vivo": false, "extra": 1},
        {"id": "f1-1", "esporte": "f1", "titulo": "GP de Abu Dhabi", "detalhe": "Corrida",
         "competicao": "Fórmula 1", "inicio": "2099-01-03T13:00:00.000Z", "duracao_min": 120,
         "link": null, "ao_vivo": false},
        {"id": "cs-1", "esporte": "cs", "titulo": "FURIA x NAVI", "detalhe": "Quartas",
         "competicao": "IEM Cologne", "inicio": "2099-01-04T15:00:00.000Z", "duracao_min": 90,
         "link": "https://exemplo.test/cs", "ao_vivo": false}
      ],
      "fontes": [
        {"nome": "cruzeiro", "atualizado_em": "2099-01-01T11:00:00.000Z", "ok": true, "erro": null},
        {"nome": "cs", "atualizado_em": null, "ok": false, "erro": "HTTP 500"}
      ]
    }
""".trimIndent()

/** [Armazem] em memória (guarda o texto bruto, como o real). */
class ArmazemEmMemoria(var texto: String? = null, var semConexao: Boolean = false) : Armazem {
    override fun ler(): Eventos? = texto?.let(::lerEventos)

    override fun guardar(texto: String) {
        this.texto = texto
        semConexao = false
    }

    override fun semConexao(): Boolean = semConexao

    override fun marcarSemConexao() {
        semConexao = true
    }
}

/**
 * Worker de mentira (MockWebServer): `/api/eventos` serve [eventos], `/app/versao.json` serve
 * [versao] (404 se nulo). [foraDoAr] derruba tudo com 503.
 */
class WorkerLocal(
    @Volatile var eventos: String = EVENTOS_JSON,
    @Volatile var versao: String? = null,
) {
    @Volatile
    var foraDoAr = false

    private val servidor = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (foraDoAr) return MockResponse(code = 503)
                return when (request.url.encodedPath) {
                    "/api/eventos" -> MockResponse(body = eventos)
                    "/app/versao.json" -> versao?.let { MockResponse(body = it) } ?: MockResponse(code = 404)
                    else -> MockResponse(code = 404)
                }
            }
        }
        start()
    }

    fun fechar() = servidor.close()

    val urlDosEventos: HttpUrl get() = servidor.url("/api/eventos")
    val urlDaVersao: HttpUrl get() = servidor.url("/app/versao.json")
}

/** Um Worker local para a JVM de testes inteira (o app de teste aponta para ele). */
object WorkerDeTeste {
    val worker: WorkerLocal by lazy { WorkerLocal() }
}

/** App dos testes de tela: o contêiner aponta para o [WorkerDeTeste] em vez da internet. */
class SportToWatchAppDeTeste : SportToWatchApp() {
    override val container: AppContainer by lazy {
        val worker = WorkerDeTeste.worker
        AppContainer.montar(this, OkHttpClient(), worker.urlDosEventos, worker.urlDaVersao)
    }
}
