package io.github.gfvdataweb.sporttowatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.gfvdataweb.sporttowatch.AppContainer
import io.github.gfvdataweb.sporttowatch.data.Armazem
import io.github.gfvdataweb.sporttowatch.data.Atualizador
import io.github.gfvdataweb.sporttowatch.data.Eventos
import io.github.gfvdataweb.sporttowatch.data.VerificadorDeAtualizacao
import io.github.gfvdataweb.sporttowatch.data.VersaoPublicada
import io.github.gfvdataweb.sporttowatch.data.haAtualizacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDaTela(
    /** Último dado lido (da rede ou guardado); null = nunca leu. */
    val eventos: Eventos? = null,
    val atualizando: Boolean = false,
    /** A última tentativa de atualizar falhou (a tela segue com o dado salvo). */
    val falhou: Boolean = false,
    /** Versão oficial mais nova que a instalada, se a consulta achou uma. */
    val versaoNova: VersaoPublicada? = null,
)

/** Busca os eventos ao abrir e ao puxar a tela; consulta o versao.json uma vez. */
class TelaViewModel(
    private val atualizador: Atualizador,
    private val armazem: Armazem,
    private val verificador: VerificadorDeAtualizacao,
    private val buildInstalado: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaTela(eventos = armazem.ler(), falhou = armazem.semConexao()))
    val estado: StateFlow<EstadoDaTela> = _estado.asStateFlow()

    init {
        atualizar()
        viewModelScope.launch {
            val publicada = verificador.maisRecente()
            _estado.update { atual -> atual.copy(versaoNova = publicada?.takeIf { haAtualizacao(it, buildInstalado) }) }
        }
    }

    fun atualizar() {
        if (_estado.value.atualizando) return
        _estado.update { atual -> atual.copy(atualizando = true) }
        viewModelScope.launch {
            val trouxe = atualizador.atualizar()
            _estado.update { atual -> atual.copy(eventos = armazem.ler(), atualizando = false, falhou = !trouxe) }
        }
    }

    companion object {
        fun fabrica(container: AppContainer, buildInstalado: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { TelaViewModel(container.atualizador, container.armazem, container.verificador, buildInstalado) }
            }
    }
}
