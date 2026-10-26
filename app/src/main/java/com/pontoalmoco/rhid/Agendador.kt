package com.pontoalmoco.rhid

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Agendador {
    const val ACAO_DISPARO = "com.pontoalmoco.rhid.DISPARO"
    const val EXTRA_TIPO = "tipo"
    const val EXTRA_TENTATIVA = "tentativa"
    const val EXTRA_FORCAR = "forcar"
    val TIPOS = listOf("saida", "volta")

    /** Próximo horário (em ms) para "HH:mm" em um dos dias permitidos. */
    fun proxima(hhmm: String, dias: Set<Int>): Long? {
        val partes = hhmm.split(":")
        val h = partes[0].toInt()
        val m = partes[1].toInt()
        val agora = System.currentTimeMillis()
        for (i in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, i)
            c.set(Calendar.HOUR_OF_DAY, h)
            c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > agora && c.get(Calendar.DAY_OF_WEEK) in dias) return c.timeInMillis
        }
        return null
    }

    private fun pendingIntent(ctx: Context, tipo: String, requestCode: Int, tentativa: Int = 0, forcar: Boolean = false): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACAO_DISPARO)
            .putExtra(EXTRA_TIPO, tipo)
            .putExtra(EXTRA_TENTATIVA, tentativa)
            .putExtra(EXTRA_FORCAR, forcar)
        return PendingIntent.getBroadcast(
            ctx, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun agendarTudo(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val cfg = Config.ler(ctx)
        for (tipo in TIPOS) {
            val pi = pendingIntent(ctx, tipo, Config.codigo(tipo))
            am.cancel(pi)
            if (!cfg.ativo) continue
            val hora = if (tipo == "saida") cfg.saida else cfg.volta
            val quando = proxima(hora, cfg.dias) ?: continue
            agendarExato(ctx, quando, pi)
        }
    }

    fun agendarNovaTentativa(ctx: Context, tipo: String, tentativa: Int, atrasoMs: Long, forcar: Boolean = false) {
        val pi = pendingIntent(ctx, tipo, Config.codigo(tipo) + 10, tentativa, forcar)
        agendarExato(ctx, System.currentTimeMillis() + atrasoMs, pi)
    }

    /** Teste real: dispara o mesmo alarme dos horários, daqui a [atrasoMs], registrando de verdade. */
    fun agendarTesteReal(ctx: Context, atrasoMs: Long): Long {
        val quando = System.currentTimeMillis() + atrasoMs
        agendarExato(ctx, quando, pendingIntent(ctx, "saida", 21, 0, true))
        return quando
    }

    fun cancelarTentativa(ctx: Context, tipo: String) {
        ctx.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(ctx, tipo, Config.codigo(tipo) + 10))
    }

    fun podeAlarmeExato(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun agendarExato(ctx: Context, quando: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (podeAlarmeExato(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }
}
