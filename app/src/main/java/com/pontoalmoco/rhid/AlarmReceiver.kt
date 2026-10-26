package com.pontoalmoco.rhid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Agendador.ACAO_DISPARO) return
        val tipo = intent.getStringExtra(Agendador.EXTRA_TIPO) ?: return
        val tentativa = intent.getIntExtra(Agendador.EXTRA_TENTATIVA, 0)
        val forcar = intent.getBooleanExtra(Agendador.EXTRA_FORCAR, false) // teste real

        if (!forcar) {
            if (tentativa == 0) Agendador.agendarTudo(ctx) // já deixa agendado o próximo dia
            val cfg = Config.ler(ctx)
            if (!cfg.ativo || Config.feitoHoje(ctx, tipo)) return
        }

        RhidActivity.iniciarAutomatico(ctx, tipo, tentativa, forcar)
    }
}
