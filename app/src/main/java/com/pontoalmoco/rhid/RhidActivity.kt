package com.pontoalmoco.rhid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Abre o RHID num WebView.
 * - tipo == null: só para você fazer login / usar o RHID (a sessão fica salva).
 * - tipo = "saida"/"volta": executa o registro. Funciona com o celular bloqueado:
 *   a tela acende sobre o bloqueio, registra e a tela fecha sozinha.
 * - teste = true: percorre o fluxo e só destaca o botão final, sem registrar.
 */
class RhidActivity : Activity() {

    companion object {
        const val MAX_TENTATIVAS = 3
        @Volatile var emExecucao = false

        fun intent(ctx: Context, tipo: String?, teste: Boolean, tentativa: Int = 0): Intent =
            Intent(ctx, RhidActivity::class.java)
                .putExtra("tipo", tipo)
                .putExtra("teste", teste)
                .putExtra("tentativa", tentativa)

        /** Chamado pelo alarme. Não exige que você esteja usando o celular. */
        fun iniciarAutomatico(ctx: Context, tipo: String, tentativa: Int) {
            if (emExecucao) return
            val i = intent(ctx, tipo, false, tentativa).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pm = ctx.getSystemService(PowerManager::class.java)
            val km = ctx.getSystemService(KeyguardManager::class.java)
            val desbloqueado = pm.isInteractive && !km.isKeyguardLocked

            // Celular bloqueado/tela apagada: notificação de tela cheia (igual despertador) abre o app sobre o bloqueio.
            if (!desbloqueado && Notificador.podeTelaCheia(ctx)) {
                Notificador.telaCheia(ctx, tipo, i)
                return
            }
            // Celular em uso (ou sem tela cheia): abre direto por cima, se "sobrepor a outros apps" estiver liberado.
            if (Settings.canDrawOverlays(ctx)) {
                try {
                    ctx.startActivity(i)
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
    private var tentativa = 0
    private var fase = "INICIO"
    private var antes = -1
    private var terminado = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val script by lazy { assets.open("rhid.js").bufferedReader().use { it.readText() } }

    private val loopInjecao = object : Runnable {
        override fun run() {
            injetar()
            if (!terminado) handler.postDelayed(this, 4000)
        }
    }

    private val automatico get() = tipo != null && !teste

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tipo = intent.getStringExtra("tipo")
        teste = intent.getBooleanExtra("teste", false)
        tentativa = intent.getIntExtra("tentativa", 0)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (automatico) prepararTelaBloqueada()

        status = TextView(this).apply {
            setPadding(40, 28, 40, 28)
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(88, 101, 216))
        }
        web = WebView(this)
        val coluna = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        coluna.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        coluna.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val raiz = FrameLayout(this)
        raiz.addView(coluna)
        if (automatico) {
            // bloqueia toques acidentais (celular no bolso) enquanto o app registra
            raiz.addView(View(this).apply { isClickable = true; isFocusable = true })
        }
        setContentView(raiz)
        configurarWebView()

        val t = tipo
        when {
            t == null -> status.text = "Faça login no RHID. A sessão fica salva no app."
            !teste && Config.feitoHoje(this, t) -> {
                terminado = true
                status.text = "A ${Config.nome(t)} de hoje já foi registrada pelo app."
                handler.postDelayed({ finish() }, 4000)
            }
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                concluir(false, "O app está sem permissão de localização. Abra o app e toque em \"Permitir localização\".", true)
            else -> {
                emExecucao = true
                Notificador.cancelarPendente(this, t)
                status.text = (if (teste) "TESTE — " else "") + "Registrando ${Config.nome(t)}…" +
                    (if (tentativa > 0) " (tentativa ${tentativa + 1})" else "")
                handler.postDelayed({ concluir(false, "Tempo esgotado (2 min) sem concluir o registro.") }, 120_000)
                handler.postDelayed(loopInjecao, 3000)
            }
        }
        web.loadUrl(Config.RHID_URL)
    }

    /** Mostra sobre a tela de bloqueio sem desbloquear, acende a tela com brilho mínimo e segura a CPU. */
    private fun prepararTelaBloqueada() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        val lp = window.attributes
        lp.screenBrightness = 0.05f
        window.attributes = lp
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PontoRHID:registro")
            .apply { acquire(3 * 60_000L) }
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

        /** Erro que pode ser resolvido tentando de novo (rede, página lenta, GPS). */
        @JavascriptInterface
        fun erro(msg: String) = runOnUiThread { if (doRhid()) concluir(false, msg) }

        /** Erro que não adianta repetir (já registrado, fora de ordem, sem login). */
        @JavascriptInterface
        fun erroFinal(msg: String) = runOnUiThread { if (doRhid()) concluir(false, msg, true) }
    }

    private fun concluir(ok: Boolean, msg: String, definitivo: Boolean = false) {
        if (terminado) return
        terminado = true
        emExecucao = false
        handler.removeCallbacksAndMessages(null)
        status.setBackgroundColor(if (ok) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40))
        val t = tipo
        if (t == null || teste) {
            status.text = (if (ok) "✔ " else "✖ ") + msg
            return
        }

        var texto = msg
        if (ok) {
            Config.marcarFeito(this, t)
        } else if (!definitivo && tentativa + 1 < MAX_TENTATIVAS) {
            Agendador.agendarNovaTentativa(this, t, tentativa + 1, 120_000L)
            texto = "$msg Nova tentativa em 2 min."
        }
        status.text = (if (ok) "✔ " else "✖ ") + texto
        Config.registrarLog(this, "${Config.nome(t)}: $texto")
        Notificador.resultado(this, ok, t, texto)
        handler.postDelayed({ finish() }, if (ok) 5000 else 8000)
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
        wakeLock?.let { if (it.isHeld) it.release() }
        web.destroy()
        super.onDestroy()
    }
}
