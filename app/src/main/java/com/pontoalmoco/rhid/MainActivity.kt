package com.pontoalmoco.rhid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    // ---------- Paleta ----------
    private val AZUL = 0xFF5865D8.toInt()
    private val AZUL_ESCURO = 0xFF3B44A8.toInt()
    private val AZUL_CLARO = 0xFFEEF0FC.toInt()
    private val FUNDO = 0xFFF3F4F8.toInt()
    private val TEXTO = 0xFF1F2333.toInt()
    private val CINZA = 0xFF6B7085.toInt()
    private val BORDA = 0xFFE3E5EE.toInt()
    private val VERDE = 0xFF2E9E5B.toInt()
    private val VERMELHO = 0xFFD64545.toInt()
    private val AMBAR = 0xFFE08A00.toInt()

    private lateinit var swAtivo: Switch
    private lateinit var txProxima: TextView
    private lateinit var txProximaSub: TextView
    private lateinit var txSaida: TextView
    private lateinit var txVolta: TextView
    private lateinit var listaPermissoes: LinearLayout
    private lateinit var listaLog: LinearLayout
    private val chipsDias = mutableListOf<Pair<Int, TextView>>()
    private val diasSelecionados = mutableSetOf<Int>()
    private var saida = "12:00"
    private var volta = "13:00"
    private var carregando = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = AZUL_ESCURO

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FUNDO)
        }
        val cabecalho = cabecalho()
        raiz.addView(cabecalho)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(col); isFillViewport = true }
        raiz.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(raiz)

        // Android 15 desenha atrás da barra de status: compensa com as margens do sistema
        raiz.setOnApplyWindowInsetsListener { _, ins ->
            val topo: Int
            val base: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                topo = b.top
                base = b.bottom
            } else {
                topo = ins.systemWindowInsetTop
                base = ins.systemWindowInsetBottom
            }
            cabecalho.setPadding(dp(20), dp(18) + topo, dp(20), dp(26))
            col.setPadding(dp(16), dp(16), dp(16), dp(24) + base)
            ins
        }

        adicionar(col, cartaoHorarios())
        adicionar(col, cartaoConfiguracao())
        adicionar(col, cartaoTestes())
        adicionar(col, cartaoHistorico())
        adicionar(col, cartaoSobre())

        carregar()
        Atualizador.verificar(this, true) // confere sozinho ao abrir o app
    }

    override fun onResume() {
        super.onResume()
        Agendador.agendarTudo(this)
        atualizarPermissoes()
        atualizarProxima()
        atualizarHistorico()
    }

    // ================= Cabeçalho =================
    private fun cabecalho(): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(AZUL, AZUL_ESCURO)).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat())
            }
            elevation = dp(4).toFloat()
        }
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        linha.addView(TextView(this).apply {
            text = "Ponto Almoço"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        swAtivo = Switch(this).apply {
            text = "Ativo  "
            setTextColor(Color.WHITE)
            textSize = 14f
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFF8BE0AE.toInt(), 0x66FFFFFF)
            )
            setOnCheckedChangeListener { _, _ -> if (!carregando) salvar() }
        }
        linha.addView(swAtivo)
        h.addView(linha)

        h.addView(TextView(this).apply {
            text = "PRÓXIMA BATIDA"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(22), 0, dp(4))
        })
        txProxima = TextView(this).apply {
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        h.addView(txProxima)
        txProximaSub = TextView(this).apply {
            textSize = 14f
            setTextColor(0xDDFFFFFF.toInt())
            setPadding(0, dp(2), 0, 0)
        }
        h.addView(txProximaSub)
        return h
    }

    private fun atualizarProxima() {
        val cfg = Config.ler(this)
        if (!cfg.ativo) {
            txProxima.text = "Desativado"
            txProximaSub.text = "Ative para o app bater o ponto sozinho"
            return
        }
        val s = Agendador.proxima(cfg.saida, cfg.dias)
        val v = Agendador.proxima(cfg.volta, cfg.dias)
        val (nome, quando) = when {
            s == null && v == null -> { txProxima.text = "Nenhum dia marcado"; txProximaSub.text = "Escolha os dias abaixo"; return }
            v == null || (s != null && s <= v) -> "Saída" to s!!
            else -> "Volta" to v!!
        }
        txProxima.text = "$nome · ${quandoTexto(quando)}"
        val min = ((quando - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        txProximaSub.text = when {
            min < 1 -> "agora"
            min < 60 -> "em $min min"
            min < 24 * 60 -> "em ${min / 60} h ${min % 60} min"
            else -> "em ${min / (24 * 60)} dia(s)"
        }
    }

    private fun quandoTexto(ms: Long): String {
        val alvo = Calendar.getInstance().apply { timeInMillis = ms }
        val hoje = Calendar.getInstance()
        val amanha = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        fun mesmoDia(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            mesmoDia(alvo, hoje) -> "hoje ${Config.hora(ms)}"
            mesmoDia(alvo, amanha) -> "amanhã ${Config.hora(ms)}"
            else -> Config.dataHora(ms)
        }
    }

    // ================= Horários =================
    private fun cartaoHorarios(): LinearLayout {
        val c = cartao("Horários", "Alterações são salvas na hora")

        val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (blocoSaida, tSaida) = blocoHora("Saída do almoço") { escolherHora(true) }
        val (blocoVolta, tVolta) = blocoHora("Volta do almoço") { escolherHora(false) }
        txSaida = tSaida
        txVolta = tVolta
        linha.addView(blocoSaida, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        linha.addView(blocoVolta, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        c.addView(linha)

        c.addView(rotulo("Dias da semana").apply { setPadding(0, dp(18), 0, dp(8)) })
        val dias = listOf(
            Calendar.MONDAY to "S", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "Q",
            Calendar.THURSDAY to "Q", Calendar.FRIDAY to "S", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D"
        )
        val linhaDias = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dias.forEach { (dia, letra) ->
            val chip = TextView(this).apply {
                text = letra
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setOnClickListener {
                    if (dia in diasSelecionados) diasSelecionados.remove(dia) else diasSelecionados.add(dia)
                    pintarDias()
                    salvar()
                }
            }
            chipsDias += dia to chip
            linhaDias.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        c.addView(linhaDias)
        return c
    }

    private fun blocoHora(rotulo: String, acao: () -> Unit): Pair<LinearLayout, TextView> {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = arredondado(AZUL_CLARO, 16)
            foreground = ripple(16)
            setOnClickListener { acao() }
        }
        b.addView(TextView(this).apply { text = rotulo; textSize = 13f; setTextColor(CINZA) })
        val hora = TextView(this).apply {
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(AZUL)
        }
        b.addView(hora)
        b.addView(TextView(this).apply { text = "toque para mudar"; textSize = 11f; setTextColor(CINZA) })
        return b to hora
    }

    private fun pintarDias() {
        chipsDias.forEach { (dia, chip) ->
            val ligado = dia in diasSelecionados
            chip.background = if (ligado) arredondado(AZUL, 21) else arredondado(Color.WHITE, 21, BORDA)
            chip.setTextColor(if (ligado) Color.WHITE else CINZA)
        }
    }

    private fun escolherHora(ehSaida: Boolean) {
        val partes = (if (ehSaida) saida else volta).split(":")
        TimePickerDialog(this, { _, h, m ->
            val v = "%02d:%02d".format(h, m)
            if (ehSaida) saida = v else volta = v
            txSaida.text = saida
            txVolta.text = volta
            salvar()
        }, partes[0].toInt(), partes[1].toInt(), true).show()
    }

    private fun carregar() {
        carregando = true
        val cfg = Config.ler(this)
        swAtivo.isChecked = cfg.ativo
        saida = cfg.saida
        volta = cfg.volta
        txSaida.text = saida
        txVolta.text = volta
        diasSelecionados.clear()
        diasSelecionados.addAll(cfg.dias)
        pintarDias()
        carregando = false
    }

    private fun salvar() {
        Config.salvar(
            this, Config.Dados(
                ativo = swAtivo.isChecked,
                saida = saida,
                volta = volta,
                dias = diasSelecionados.toSet(),
                tolerancia = Config.ler(this).tolerancia
            )
        )
        Agendador.agendarTudo(this)
        atualizarProxima()
    }

    // ================= Configuração / permissões =================
    private fun cartaoConfiguracao(): LinearLayout {
        val c = cartao("Configuração", "Toque nos itens vermelhos para liberar")
        listaPermissoes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listaPermissoes)
        return c
    }

    private fun atualizarPermissoes() {
        val ok = PackageManager.PERMISSION_GRANTED
        val locNotif = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == ok &&
            (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == ok)
        val itens = listOf<Triple<String, Boolean, () -> Unit>>(
            Triple("Localização e notificações", locNotif, { pedirPermissoes() }),
            Triple("Alarmes exatos", Agendador.podeAlarmeExato(this), { abrirAlarmes() }),
            Triple("Tela cheia (celular bloqueado)", Notificador.podeTelaCheia(this), { abrirTelaCheia() }),
            Triple("Sobrepor a outros apps", Settings.canDrawOverlays(this), { abrirSobrepor() }),
            Triple("Sem economia de bateria",
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName), { abrirBateria() })
        )
        listaPermissoes.removeAllViews()
        itens.forEach { (nome, liberado, acao) ->
            listaPermissoes.addView(linhaItem(
                if (liberado) "✓" else "!", if (liberado) VERDE else VERMELHO,
                nome, if (liberado) "Liberado" else "Toque para liberar", acao
            ))
        }
        listaPermissoes.addView(linhaItem("→", AZUL, "Login no RHID", "Abrir o RHID para entrar ou conferir") {
            startActivity(RhidActivity.intent(this, null, false))
        })
    }

    private fun linhaItem(icone: String, cor: Int, titulo: String, sub: String, acao: () -> Unit): LinearLayout {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            foreground = ripple(12)
            setOnClickListener { acao() }
        }
        l.addView(TextView(this).apply {
            text = icone
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cor) }
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        val textos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        textos.addView(TextView(this).apply { text = titulo; textSize = 15f; setTextColor(TEXTO) })
        textos.addView(TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(if (cor == VERMELHO) VERMELHO else CINZA)
        })
        l.addView(textos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        l.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(BORDA) })
        return l
    }

    private fun pedirPermissoes() {
        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) lista += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(lista.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        atualizarPermissoes()
    }

    private fun abrirAlarmes() {
        if (Agendador.podeAlarmeExato(this)) {
            toast("Alarmes exatos já liberados.")
        } else if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun abrirTelaCheia() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } else toast("Já permitido nesta versão do Android.")
    }

    private fun abrirSobrepor() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    @SuppressLint("BatteryLife")
    private fun abrirBateria() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    // ================= Testes =================
    private fun cartaoTestes(): LinearLayout {
        val c = cartao("Testes", null)

        c.addView(rotulo("Simulação — não registra").apply { setPadding(0, 0, 0, dp(8)) })
        val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        linha.addView(botao("Testar saída", AZUL, false) { startActivity(RhidActivity.intent(this, "saida", true)) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        linha.addView(botao("Testar volta", AZUL, false) { startActivity(RhidActivity.intent(this, "volta", true)) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        c.addView(linha)

        val real = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = arredondado(0xFFFFF4E0.toInt(), 14)
        }
        real.addView(TextView(this).apply {
            text = "⚠  Teste real — registra de verdade"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(AMBAR)
        })
        real.addView(TextView(this).apply {
            text = "Faz o fluxo completo e grava uma batida no RHID (apague depois). Não mexe nos horários agendados."
            textSize = 12f
            setTextColor(CINZA)
            setPadding(0, dp(4), 0, dp(10))
        })
        real.addView(botao("Registrar agora", AMBAR, true) {
            startActivity(RhidActivity.intent(this, "saida", false, 0, true))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
        real.addView(botao("Simular alarme em 1 min (bloqueie o celular)", AMBAR, false) {
            val quando = Agendador.agendarTesteReal(this, 60_000L)
            toast("Alarme de teste às ${Config.hora(quando)}. Bloqueie o celular e aguarde.")
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) })
        c.addView(real, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        return c
    }

    // ================= Histórico =================
    private fun cartaoHistorico(): LinearLayout {
        val c = cartao("Histórico", null)
        listaLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listaLog)
        c.addView(TextView(this).apply {
            text = "Limpar histórico"
            textSize = 13f
            setTextColor(CINZA)
            gravity = Gravity.END
            setPadding(0, dp(10), dp(4), 0)
            setOnClickListener { Config.limparLog(this@MainActivity); atualizarHistorico() }
        })
        return c
    }

    private fun atualizarHistorico() {
        listaLog.removeAllViews()
        val linhas = Config.log(this).lines().filter { it.isNotBlank() }.take(15)
        if (linhas.isEmpty()) {
            listaLog.addView(TextView(this).apply {
                text = "Nenhum registro ainda."
                textSize = 13f
                setTextColor(CINZA)
            })
            return
        }
        linhas.forEach { linha ->
            val partes = linha.split(" — ", limit = 2)
            val hora = partes.getOrElse(0) { "" }
            val msg = partes.getOrElse(1) { linha }
            val baixo = msg.lowercase()
            val cor = when {
                listOf("não registr", "falh", "erro", "interromp", "não deixou", "esgotado", "não apareceu", "não encontrado", "expirada", "fora de ordem").any { it in baixo } -> VERMELHO
                listOf("confirmada", "teste ok").any { it in baixo } -> VERDE
                "alarme tocou" in baixo -> AZUL
                else -> CINZA
            }
            val l = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, dp(7))
            }
            l.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cor) }
            }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { topMargin = dp(5); marginEnd = dp(12) })
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(TextView(this).apply {
                text = hora
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(CINZA)
            })
            t.addView(TextView(this).apply { text = msg; textSize = 13f; setTextColor(TEXTO) })
            l.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            listaLog.addView(l)
        }
    }

    // ================= Sobre =================
    private fun cartaoSobre(): LinearLayout {
        val c = cartao("Atualização", "Versão instalada: ${Atualizador.nomeInstalado(this)}")
        c.addView(botao("Verificar atualização", AZUL, true) { Atualizador.verificar(this, false) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
        return c
    }

    // ================= Componentes visuais =================
    private fun cartao(titulo: String, subtitulo: String?): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = arredondado(Color.WHITE, 20)
            elevation = dp(1).toFloat()
        }
        c.addView(TextView(this).apply {
            text = titulo
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXTO)
        })
        c.addView(TextView(this).apply {
            text = subtitulo ?: ""
            textSize = 12f
            setTextColor(CINZA)
            setPadding(0, dp(2), 0, dp(14))
            if (subtitulo == null) { textSize = 1f; setPadding(0, 0, 0, dp(10)) }
        })
        return c
    }

    private fun adicionar(col: LinearLayout, v: View) {
        col.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })
    }

    private fun rotulo(t: String) = TextView(this).apply {
        text = t.uppercase()
        textSize = 11f
        letterSpacing = 0.1f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(CINZA)
    }

    private fun botao(t: String, cor: Int, cheio: Boolean, acao: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (cheio) Color.WHITE else cor)
        background = if (cheio) arredondado(cor, 14) else arredondado(Color.TRANSPARENT, 14, cor)
        foreground = ripple(14)
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { acao() }
    }

    private fun arredondado(cor: Int, raioDp: Int, borda: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(raioDp).toFloat()
        setColor(cor)
        if (borda != null) setStroke(dp(1).coerceAtLeast(2), borda)
    }

    private fun ripple(raioDp: Int): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x22000000), null, arredondado(Color.BLACK, raioDp))

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
