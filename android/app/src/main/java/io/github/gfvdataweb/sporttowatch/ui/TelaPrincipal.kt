package io.github.gfvdataweb.sporttowatch.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gfvdataweb.sporttowatch.R
import io.github.gfvdataweb.sporttowatch.data.Esporte
import io.github.gfvdataweb.sporttowatch.data.Evento
import io.github.gfvdataweb.sporttowatch.data.agruparPorEsporte
import io.github.gfvdataweb.sporttowatch.data.aoVivo
import io.github.gfvdataweb.sporttowatch.data.linkSeguro
import java.time.Instant

@StringRes
private fun Esporte.rotulo(): Int = when (this) {
    Esporte.CRUZEIRO -> R.string.esporte_cruzeiro
    Esporte.F1 -> R.string.esporte_f1
    Esporte.CS -> R.string.esporte_cs
    Esporte.OUTRO -> R.string.esporte_outro
}

/**
 * A única tela: eventos agrupados por esporte, puxar para atualizar, aviso de versão nova e
 * como pôr o widget. Recebe dados prontos e callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPrincipal(
    estado: EstadoDaTela,
    versao: String,
    build: Int,
    podeFixarWidget: Boolean,
    aoAtualizar: () -> Unit,
    aoFixarWidget: () -> Unit,
    aoBaixar: (String) -> Unit,
    aoAbrirLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val agora = remember(estado.eventos, estado.atualizando) { Instant.now() }
    val grupos = remember(estado.eventos, agora) { estado.eventos?.let { agruparPorEsporte(it.eventos, agora) }.orEmpty() }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PullToRefreshBox(
            isRefreshing = estado.atualizando,
            onRefresh = aoAtualizar,
            modifier = Modifier.safeDrawingPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) }

                val novaVersao = estado.versaoNova
                if (novaVersao != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.versao_nova, novaVersao.versao),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                OutlinedButton(onClick = { aoBaixar(novaVersao.urlDoApk) }) {
                                    Text(stringResource(R.string.versao_baixar))
                                }
                            }
                        }
                    }
                }

                if (estado.falhou) {
                    item { Text(stringResource(R.string.falha_ao_atualizar), color = MaterialTheme.colorScheme.error) }
                }
                if (estado.eventos == null) {
                    if (!estado.atualizando) item { Text(stringResource(R.string.sem_dados)) }
                } else if (grupos.isEmpty()) {
                    item { Text(stringResource(R.string.sem_eventos)) }
                }

                for (grupo in grupos) {
                    item(key = "esporte-${grupo.esporte.codigo}") {
                        Text(
                            "${grupo.esporte.icone} ${stringResource(grupo.esporte.rotulo())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(grupo.esporte.corArgb),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(grupo.eventos) { evento ->
                        CartaoDoEvento(evento, agora, aoAbrirLink)
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.widget_titulo), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.widget_instrucao), style = MaterialTheme.typography.bodyMedium)
                        if (podeFixarWidget) {
                            OutlinedButton(onClick = aoFixarWidget) { Text(stringResource(R.string.widget_adicionar)) }
                        }
                        Text(
                            stringResource(R.string.versao_instalada, versao, build),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoDoEvento(evento: Evento, agora: Instant, aoAbrirLink: (String) -> Unit) {
    val link = linkSeguro(evento.link)
    val cor = Color(evento.esporte.corArgb)
    Card(
        onClick = { if (link != null) aoAbrirLink(link) },
        enabled = link != null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatarDataHora(evento.inicio),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = cor,
                )
                if (aoVivo(evento, agora)) {
                    Text(
                        stringResource(R.string.ao_vivo),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(evento.titulo, style = MaterialTheme.typography.titleMedium)
            val sub = listOf(evento.competicao, evento.detalhe).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
