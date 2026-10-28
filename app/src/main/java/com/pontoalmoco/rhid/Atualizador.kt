package com.pontoalmoco.rhid

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Atualiza o app a partir das Releases do GitHub (Zhanixx/RHponto-APK). */
object Atualizador {
    private const val API = "https://api.github.com/repos/Zhanixx/RHponto-APK/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    data class Versao(val codigo: Int, val nome: String, val url: String, val notas: String)

    fun versaoInstalada(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }

    fun nomeInstalado(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Consulta a última Release. silencioso = não mostra nada se já estiver atualizado ou sem internet. */
    fun verificar(act: Activity, silencioso: Boolean) {
        Thread {
            val resultado = try { buscarUltima() } catch (e: Exception) { null }
            main.post {
                if (act.isFinishing) return@post
                when {
                    resultado == null -> if (!silencioso) aviso(act, "Não consegui verificar agora. Confira a internet.")
                    resultado.codigo <= versaoInstalada(act) -> if (!silencioso) aviso(act, "Você já está na versão mais nova.")
                    else -> perguntar(act, resultado)
                }
            }
        }.start()
    }

    private fun buscarUltima(): Versao? {
        val con = URL(API).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "PontoAlmocoRHID")
        con.connectTimeout = 15000
        con.readTimeout = 15000
        val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        val tag = json.getString("tag_name")                       // "build-7"
        val numero = Regex("(\\d+)").find(tag)?.value?.toInt() ?: return null
        val assets = json.getJSONArray("assets")
        if (assets.length() == 0) return null
        val url = assets.getJSONObject(0).getString("browser_download_url")
        return Versao(100 + numero, "2.2.$numero", url, json.optString("body", ""))
    }

    private fun perguntar(act: Activity, v: Versao) {
        AlertDialog.Builder(act)
            .setTitle("Nova versão disponível")
            .setMessage("Versão ${v.nome} (você tem ${nomeInstalado(act)}).\n\n${v.notas}".trim())
            .setPositiveButton("Atualizar") { _, _ -> baixarEInstalar(act, v) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun baixarEInstalar(act: Activity, v: Versao) {
        if (!act.packageManager.canRequestPackageInstalls()) {
            aviso(act, "Permita \"instalar apps desconhecidos\" para o Ponto Almoço RHID e toque em Atualizar de novo.")
            act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
            return
        }
        aviso(act, "Baixando atualização…")
        Thread {
            try {
                val arquivo = File(act.cacheDir, "atualizacao.apk")
                baixar(v.url, arquivo)
                instalar(act.applicationContext, arquivo)
            } catch (e: Exception) {
                main.post { aviso(act, "Falha ao baixar: ${e.message}") }
            }
        }.start()
    }

    private fun baixar(endereco: String, destino: File) {
        var url = URL(endereco)
        var con: HttpURLConnection
        var saltos = 0
        while (true) {   // segue os redirecionamentos do GitHub
            con = url.openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.setRequestProperty("User-Agent", "PontoAlmocoRHID")
            con.connectTimeout = 20000
            con.readTimeout = 60000
            val codigo = con.responseCode
            if (codigo in 300..399 && saltos < 5) {
                url = URL(url, con.getHeaderField("Location"))
                saltos++
                continue
            }
            if (codigo != 200) throw IllegalStateException("HTTP $codigo")
            break
        }
        con.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
    }

    private fun instalar(ctx: Context, apk: File) {
        val instalador = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sessao ->
            sessao.openWrite("app.apk", 0, apk.length()).use { saida ->
                apk.inputStream().use { it.copyTo(saida) }
                sessao.fsync(saida)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val retorno = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            sessao.commit(retorno.intentSender)
        }
    }

    private fun aviso(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}
