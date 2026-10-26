package com.pontoalmoco.rhid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Abre o RHID num WebView.
 * - tipo == null: só para você fazer login / usar o RHID (a sessão fica salva).
 * - tipo = "saida"/"volta": executa o fluxo de registro (teste = só destaca o botão final).
 */
class RhidActivity : Activity() {

    companion object {
        @Volatile var emExecucao = false

        fun intent(ctx: Context, tipo: String?, teste: Boolean): Intent =
            Intent(ctx, RhidActivity::class.java)
                .putExtra("tipo", tipo)
                .putExtra("teste", teste)

        /** Chamado pelo alarme quando você está usando o celular. */
        fun iniciarAutomatico(ctx: Context, tipo: String) {
            if (emExecucao) return
            if (Settings.canDrawOverlays(ctx)) {
                try {
                    ctx.startActivity(intent(ctx, tipo, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: Exception) { }
            }
            Notificador.tocarParaRegistrar(ctx, tipo)
        }
    }

    private lateinit var web: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var tipo: String? = null
    private var teste = false
    private var fase = "INICIO"
    private var antes = -1
    private var terminado = false
    private val script by lazy { assets.open("rhid.js").bufferedReader().use { it.readText() } }

    private val loopInjecao = object : Runnable {
        override fun run() {
            injetar()
            if (!terminado) handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tipo = intent.getStringExtra("tipo")
        teste = intent.getBooleanExtra("teste", false)

        status = TextView(this).apply {
            setPadding(40, 28, 40, 28)
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(88, 101, 216))
        }
        web = WebView(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        raiz.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        configurarWebView()

        val t = tipo
        when {
            t == null -> status.text = "Faça login no RHID. A sessão fica salva no app."
            !teste && Config.feitoHoje(this, t) -> {
                terminado = true
                status.text = "A ${Config.nome(t)} de hoje já foi registrada pelo app."
            }
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                concluir(false, "O app está sem permissão de localização. Abra o app e toque em \"Permitir localização\".")
            }
            else -> {
                emExecucao = true
                Notificador.cancelarPendente(this, t)
                status.text = (if (teste) "TESTE — " else "") + "Registrando ${Config.nome(t)}…"
                handler.postDelayed({ concluir(false, "Tempo esgotado (2 min) sem concluir o registro.") }, 120_000)
                handler.postDelayed(loopInjecao, 3000)
            }
        }
        web.loadUrl(Config.RHID_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setGeolocationEnabled(true)
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Ponte(), "PontoApp")

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                if (tipo != null) injetar()
            }
        }
        // Responde o popup "permitir localização" só para o RHID — usa a localização real do celular.
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val permitido = Uri.parse(origin).host?.endsWith("rhid.com.br") == true
                callback.invoke(origin, permitido, permitido)
            }
        }
    }

    private fun doRhid(): Boolean = Uri.parse(web.url ?: "").host?.endsWith("rhid.com.br") == true

    private fun injetar() {
        if (terminado || tipo == null || !doRhid()) return
        val cfg = JSONObject()
            .put("tipo", tipo)
            .put("teste", teste)
            .put("fase", fase)
            .put("antes", antes)
        web.evaluateJavascript("$script\n;rhidRun($cfg);", null)
    }

    inner class Ponte {
        @JavascriptInterface
        fun fase(novaFase: String, contagem: Int) = runOnUiThread {
            if (!doRhid()) return@runOnUiThread
            this@RhidActivity.fase = novaFase
            if (contagem >= 0) this@RhidActivity.antes = contagem
        }

        @JavascriptInterface
        fun sucesso(msg: String) = runOnUiThread { if (doRhid()) concluir(true, msg) }

        @JavascriptInterface
        fun erro(msg: String) = runOnUiThread { if (doRhid()) concluir(false, msg) }
    }

    private fun concluir(ok: Boolean, msg: String) {
        if (terminado) return
        terminado = true
        emExecucao = false
        handler.removeCallbacksAndMessages(null)
        status.text = (if (ok) "✔ " else "✖ ") + msg
        status.setBackgroundColor(if (ok) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40))
        val t = tipo ?: return
        if (teste) return
        if (ok) Config.marcarFeito(this, t)
        Config.registrarLog(this, "${Config.nome(t)}: $msg")
        Notificador.resultado(this, ok, t, msg)
        if (ok) handler.postDelayed({ finish() }, 5000)
    }

    override fun onDestroy() {
        if (!terminado && tipo != null && !teste) {
            terminado = true
            val t = tipo!!
            Config.registrarLog(this, "${Config.nome(t)}: registro interrompido (tela fechada)")
            Notificador.resultado(this, false, t, "O registro foi interrompido antes de terminar. Confira no RHID.")
        }
        emExecucao = false
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
