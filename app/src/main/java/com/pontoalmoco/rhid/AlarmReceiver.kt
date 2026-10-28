package com.pontoalmoco.rhid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Agendador.ACAO_DISPARO) return
        val tipo = intent.getStringExtra(Agendador.EXTRA_TIPO) ?: return
        val tentativa = intent.getIntExtra(Agendador.EXTRA_TENTATIVA, 0)
        val forcar = intent.getBooleanExtra(Agendador.EXTRA_FORCAR, false) // teste real
        val nome = if (forcar) "teste real" else Config.nome(tipo)

        if (!forcar) {
            if (tentativa == 0) Agendador.agendarTudo(ctx) // já deixa agendado o próximo dia
            val cfg = Config.ler(ctx)
            if (!cfg.ativo) { Config.registrarLog(ctx, "$nome: alarme tocou, mas o app está desativado"); return }
            if (Config.feitoHoje(ctx, tipo)) { Config.registrarLog(ctx, "$nome: alarme tocou, já registrada hoje"); return }
        }

        val caminho = RhidActivity.iniciarAutomatico(ctx, tipo, tentativa, forcar)
        Config.registrarLog(ctx, "$nome: alarme tocou (${caminho})" + if (tentativa > 0) " — tentativa ${tentativa + 1}" else "")

        // Confere se a tela de registro abriu mesmo. Se o Android bloqueou, avisa para você tocar.
        val pendente = goAsync()
        val disparo = System.currentTimeMillis()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val abriu = RhidActivity.ultimaAbertura >= disparo - 2000
                if (!abriu && !RhidActivity.emExecucao) {
                    Config.registrarLog(ctx, "$nome: o Android não deixou abrir sozinho — notificação para tocar enviada")
                    Notificador.tocarParaRegistrar(ctx, tipo, RhidActivity.intent(ctx, tipo, false, tentativa, forcar))
                }
            } finally {
                pendente.finish()
            }
        }, 8000)
    }
}
