package com.pontoalmoco.rhid

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Config {
    const val RHID_URL = "https://www.rhid.com.br/v2/#/dashboard"
    private const val PREFS = "config"
    private val BR = Locale("pt", "BR")

    data class Dados(
        val ativo: Boolean,
        val saida: String,
        val volta: String,
        val dias: Set<Int>,      // Calendar.SUNDAY (1) ... Calendar.SATURDAY (7)
        val tolerancia: Int      // minutos esperando você usar o celular
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ler(ctx: Context): Dados {
        val p = prefs(ctx)
        val diasPadrao = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        return Dados(
            ativo = p.getBoolean("ativo", true),
            saida = p.getString("saida", "12:00") ?: "12:00",
            volta = p.getString("volta", "13:00") ?: "13:00",
            dias = p.getStringSet("dias", null)?.map { it.toInt() }?.toSet() ?: diasPadrao,
            tolerancia = p.getInt("tolerancia", 30)
        )
    }

    fun salvar(ctx: Context, d: Dados) {
        prefs(ctx).edit()
            .putBoolean("ativo", d.ativo)
            .putString("saida", d.saida)
            .putString("volta", d.volta)
            .putStringSet("dias", d.dias.map { it.toString() }.toSet())
            .putInt("tolerancia", d.tolerancia)
            .apply()
    }

    fun nome(tipo: String?) = when (tipo) {
        "saida" -> "saída do almoço"
        "volta" -> "volta do almoço"
        else -> "ponto"
    }

    fun codigo(tipo: String?) = if (tipo == "saida") 1 else 2

    private fun hoje() = SimpleDateFormat("yyyy-MM-dd", BR).format(Date())

    fun feitoHoje(ctx: Context, tipo: String) = prefs(ctx).getString("feito_$tipo", "") == hoje()

    fun marcarFeito(ctx: Context, tipo: String) {
        prefs(ctx).edit().putString("feito_$tipo", hoje()).apply()
    }

    fun registrarLog(ctx: Context, msg: String) {
        val p = prefs(ctx)
        val linha = SimpleDateFormat("dd/MM HH:mm", BR).format(Date()) + " — " + msg
        val atuais = (p.getString("log", "") ?: "").lines().filter { it.isNotBlank() }
        p.edit().putString("log", (listOf(linha) + atuais).take(30).joinToString("\n")).apply()
    }

    fun log(ctx: Context): String = prefs(ctx).getString("log", "") ?: ""

    fun hora(ms: Long): String = SimpleDateFormat("HH:mm", BR).format(Date(ms))

    fun dataHora(ms: Long): String = SimpleDateFormat("EEE dd/MM 'às' HH:mm", BR).format(Date(ms))
}
