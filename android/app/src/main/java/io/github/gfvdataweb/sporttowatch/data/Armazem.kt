package io.github.gfvdataweb.sporttowatch.data

import android.content.Context
import androidx.core.content.edit

/** Guarda a última resposta do Worker, para o widget e a tela mostrarem algo sem rede. */
interface Armazem {
    /** Último dado guardado, ou null se nunca baixou (ou o guardado não é legível). */
    fun ler(): Eventos?

    /** Guarda a resposta bruta já validada e limpa a marca de "sem conexão". */
    fun guardar(texto: String)

    /** A última tentativa de atualizar falhou? */
    fun semConexao(): Boolean

    fun marcarSemConexao()
}

/** [Armazem] em SharedPreferences (arquivo fora do backup, ver `xml/backup_rules.xml`). */
class ArmazemPreferencias(contexto: Context) : Armazem {

    private val preferencias = contexto.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    override fun ler(): Eventos? {
        val texto = preferencias.getString(RESPOSTA, null) ?: return null
        return try {
            lerEventos(texto)
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
    }

    override fun guardar(texto: String) {
        preferencias.edit {
            putString(RESPOSTA, texto)
            putBoolean(SEM_CONEXAO, false)
        }
    }

    override fun semConexao(): Boolean = preferencias.getBoolean(SEM_CONEXAO, false)

    override fun marcarSemConexao() {
        preferencias.edit { putBoolean(SEM_CONEXAO, true) }
    }

    private companion object {
        const val ARQUIVO = "ultimos_eventos"
        const val RESPOSTA = "resposta"
        const val SEM_CONEXAO = "sem_conexao"
    }
}
