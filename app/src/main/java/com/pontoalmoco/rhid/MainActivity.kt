package com.pontoalmoco.rhid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var swAtivo: Switch
    private lateinit var btSaida: Button
    private lateinit var btVolta: Button
    private lateinit var txProximos: TextView
    private lateinit var txPermissoes: TextView
    private lateinit var txLog: TextView
    private val checksDias = mutableListOf<Pair<Int, CheckBox>>()
    private var saida = "12:00"
    private var volta = "13:00"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Ponto Almoço RHID"

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(titulo("Horários"))
        swAtivo = Switch(this).apply { text = "Ativado"; textSize = 16f }
        col.addView(swAtivo)
        btSaida = botao("") { escolherHora(true) }
        btVolta = botao("") { escolherHora(false) }
        col.addView(btSaida)
        col.addView(btVolta)

        col.addView(titulo("Dias"))
        val dias = listOf(
            Calendar.MONDAY to "Seg", Calendar.TUESDAY to "Ter", Calendar.WEDNESDAY to "Qua",
            Calendar.THURSDAY to "Qui", Calendar.FRIDAY to "Sex", Calendar.SATURDAY to "Sáb",
            Calendar.SUNDAY to "Dom"
        )
        dias.chunked(4).forEach { grupo ->
            val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grupo.forEach { (dia, nome) ->
                val cb = CheckBox(this).apply { text = nome }
                checksDias += dia to cb
                linha.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            repeat(4 - grupo.size) { linha.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            col.addView(linha)
        }

        col.addView(texto("O ponto é batido sozinho, mesmo com o celular bloqueado: a tela acende por alguns segundos sobre o bloqueio, registra e apaga. Se falhar (rede/GPS), tenta de novo mais 2 vezes, a cada 2 min."))

        col.addView(botao("Salvar") { salvar() })
        txProximos = texto("")
        col.addView(txProximos)

        col.addView(titulo("Configuração inicial"))
        txPermissoes = texto("")
        col.addView(txPermissoes)
        col.addView(botao("1. Permitir localização e notificações") { pedirPermissoes() })
        col.addView(botao("2. Permitir alarmes exatos") { abrirAlarmes() })
        col.addView(botao("3. Permitir sobrepor a outros apps") { abrirSobrepor() })
        col.addView(botao("4. Ignorar economia de bateria") { abrirBateria() })
        col.addView(botao("5. Permitir tela cheia (registrar com celular bloqueado)") { abrirTelaCheia() })
        col.addView(botao("6. Abrir RHID e fazer login") { startActivity(RhidActivity.intent(this, null, false)) })

        col.addView(titulo("Testes (não registram ponto)"))
        col.addView(botao("Testar saída") { startActivity(RhidActivity.intent(this, "saida", true)) })
        col.addView(botao("Testar volta") { startActivity(RhidActivity.intent(this, "volta", true)) })

        col.addView(titulo("Teste real (registra de verdade)"))
        col.addView(texto("Faz o fluxo completo e REGISTRA uma batida no RHID (apague depois se precisar). Não interfere nos horários agendados."))
        col.addView(botao("Registrar agora (real)") {
            startActivity(RhidActivity.intent(this, "saida", false, 0, true))
        })
        col.addView(botao("Simular alarme em 1 minuto (real) — bloqueie o celular") {
            val quando = Agendador.agendarTesteReal(this, 60_000L)
            Toast.makeText(this, "Alarme de teste às ${Config.hora(quando)}. Bloqueie o celular e aguarde.", Toast.LENGTH_LONG).show()
        })

        col.addView(titulo("Histórico"))
        txLog = texto("")
        col.addView(txLog)

        col.addView(titulo("Atualização"))
        col.addView(texto("Versão instalada: ${Atualizador.nomeInstalado(this)}"))
        col.addView(botao("Verificar atualização") { Atualizador.verificar(this, false) })

        carregar()
        Atualizador.verificar(this, true) // confere sozinho ao abrir o app
    }

    override fun onResume() {
        super.onResume()
        Agendador.agendarTudo(this)
        atualizarPermissoes()
        mostrarProximos()
        txLog.text = Config.log(this).ifBlank { "Nenhum registro ainda." }
    }

    private fun carregar() {
        val cfg = Config.ler(this)
        swAtivo.isChecked = cfg.ativo
        saida = cfg.saida
        volta = cfg.volta
        checksDias.forEach { (dia, cb) -> cb.isChecked = dia in cfg.dias }
        atualizarBotoesHora()
    }

    private fun atualizarBotoesHora() {
        btSaida.text = "Saída do almoço: $saida"
        btVolta.text = "Volta do almoço: $volta"
    }

    private fun escolherHora(ehSaida: Boolean) {
        val partes = (if (ehSaida) saida else volta).split(":")
        TimePickerDialog(this, { _, h, m ->
            val v = "%02d:%02d".format(h, m)
            if (ehSaida) saida = v else volta = v
            atualizarBotoesHora()
        }, partes[0].toInt(), partes[1].toInt(), true).show()
    }

    private fun salvar() {
        Config.salvar(
            this, Config.Dados(
                ativo = swAtivo.isChecked,
                saida = saida,
                volta = volta,
                dias = checksDias.filter { it.second.isChecked }.map { it.first }.toSet(),
                tolerancia = Config.ler(this).tolerancia
            )
        )
        Agendador.agendarTudo(this)
        mostrarProximos()
        Toast.makeText(this, "Salvo ✔", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarProximos() {
        val cfg = Config.ler(this)
        if (!cfg.ativo) { txProximos.text = "Desativado — nenhuma batida agendada."; return }
        val s = Agendador.proxima(cfg.saida, cfg.dias)
        val v = Agendador.proxima(cfg.volta, cfg.dias)
        txProximos.text = "Próximas batidas:\n" +
            "Saída — " + (s?.let { Config.dataHora(it) } ?: "nenhuma") + "\n" +
            "Volta — " + (v?.let { Config.dataHora(it) } ?: "nenhuma")
    }

    // ---------- Permissões ----------
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
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Já permitido nesta versão do Android.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirSobrepor() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun abrirTelaCheia() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Já permitido nesta versão do Android.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun abrirBateria() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun atualizarPermissoes() {
        val ok = PackageManager.PERMISSION_GRANTED
        val itens = listOf(
            "Localização" to (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == ok),
            "Notificações" to (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == ok),
            "Alarmes exatos" to Agendador.podeAlarmeExato(this),
            "Sobrepor a outros apps" to Settings.canDrawOverlays(this),
            "Sem economia de bateria" to getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            "Tela cheia (celular bloqueado)" to Notificador.podeTelaCheia(this)
        )
        txPermissoes.text = itens.joinToString("\n") { (nome, liberado) -> (if (liberado) "✔ " else "✖ ") + nome }
    }

    // ---------- Helpers de UI ----------
    private fun titulo(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF5865D8.toInt())
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun texto(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun botao(t: String, acao: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { acao() }
    }
}
