package com.pontoalmoco.rhid

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Agendador.ACAO_DISPARO) return
        val tipo = intent.getStringExtra(Agendador.EXTRA_TIPO) ?: return
        val tentativa = intent.getBooleanExtra(Agendador.EXTRA_TENTATIVA, false)
        val cfg = Config.ler(ctx)

        val limite: Long
        if (tentativa) {
            limite = intent.getLongExtra(Agendador.EXTRA_LIMITE, 0L)
        } else {
            Agendador.agendarTudo(ctx) // já deixa agendado o próximo dia
            limite = System.currentTimeMillis() + cfg.tolerancia * 60_000L
        }

        if (!cfg.ativo || Config.feitoHoje(ctx, tipo)) return

        if (System.currentTimeMillis() > limite) {
            Notificador.cancelarPendente(ctx, tipo)
            val msg = "Você não usou o celular dentro da tolerância. Registre manualmente."
            Config.registrarLog(ctx, "${Config.nome(tipo)} NÃO registrada — ausente")
            Notificador.resultado(ctx, false, tipo, msg)
            return
        }

        if (emUso(ctx)) {
            Agendador.cancelarTentativa(ctx, tipo)
            RhidActivity.iniciarAutomatico(ctx, tipo)
        } else {
            if (!tentativa) Notificador.pendente(ctx, tipo, limite)
            Agendador.agendarNovaTentativa(ctx, tipo, limite)
        }
    }

    /** "Em uso" = tela ligada e celular desbloqueado. */
    private fun emUso(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return pm.isInteractive && !km.isKeyguardLocked
    }
}
