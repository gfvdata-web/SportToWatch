package io.github.gfvdataweb.sporttowatch.data

/**
 * Busca os eventos, guarda e avisa quem desenha (o widget). Usado pelo worker e pela tela.
 * Falhou? O dado salvo fica como está e só ganha a marca "sem conexão".
 */
class Atualizador(
    private val cliente: ClienteEventos,
    private val armazem: Armazem,
    private val aoMudar: () -> Unit,
) {
    /** true se trouxe dado novo. */
    suspend fun atualizar(): Boolean {
        val texto = cliente.buscar()
        if (texto == null) armazem.marcarSemConexao() else armazem.guardar(texto)
        aoMudar()
        return texto != null
    }
}
