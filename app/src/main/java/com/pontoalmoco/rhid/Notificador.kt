package com.pontoalmoco.rhid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notificador {
    private const val CANAL = "ponto"

    private fun mostrar(ctx: Context, id: Int, titulo: String, texto: String, abrir: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Ponto", NotificationManager.IMPORTANCE_HIGH))

        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, id, abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    fun pendente(ctx: Context, tipo: String, limite: Long) = mostrar(
        ctx, 100 + Config.codigo(tipo),
        "Ponto pendente (${Config.nome(tipo)})",
        "Toque aqui para registrar agora, ou desbloqueie o celular até ${Config.hora(limite)} que eu registro.",
        RhidActivity.intent(ctx, tipo, false)
    )

    fun tocarParaRegistrar(ctx: Context, tipo: String) = mostrar(
        ctx, 100 + Config.codigo(tipo),
        "Hora do ponto (${Config.nome(tipo)})",
        "Toque para registrar. Para registrar sem tocar, permita \"Sobrepor a outros apps\" no app.",
        RhidActivity.intent(ctx, tipo, false)
    )

    fun cancelarPendente(ctx: Context, tipo: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(100 + Config.codigo(tipo))
    }

    fun resultado(ctx: Context, ok: Boolean, tipo: String, msg: String) = mostrar(
        ctx, 200 + Config.codigo(tipo),
        if (ok) "Ponto registrado ✔ (${Config.nome(tipo)})" else "Ponto NÃO registrado (${Config.nome(tipo)})",
        msg,
        RhidActivity.intent(ctx, null, false)
    )
}
