// Fluxo de registro no RHID. Injetado pelo app a cada carregamento; retoma pela "fase".
// Fases: INICIO (painel) -> MARCACAO (tela do mapa) -> VERIFICAR (painel de novo).
function rhidRun(cfg) {
  if (window.__rhidRodando) return;
  window.__rhidRodando = true;

  var fase = cfg.fase;
  var antes = cfg.antes;
  var nomeTipo = cfg.tipo === "saida" ? "saída" : "volta";

  var dormir = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  var txt = function (e) { return (e.textContent || e.value || "").replace(/\s+/g, " ").trim(); };
  var corpo = function () { return document.body ? document.body.innerText : ""; };
  var semLogin = function () { return !!document.querySelector("input[type=password]"); };
  var noPainel = function () { return /ponto di[áa]rio/i.test(corpo()); };
  var naMarcacao = function () { return /confirme seu local/i.test(corpo()); };
  var botoes = function () {
    return Array.prototype.slice.call(document.querySelectorAll("button, a, input[type=button], input[type=submit]"))
      .filter(function (b) { return /registrar\s*ponto/i.test(txt(b)) && b.offsetParent !== null; });
  };
  // conta as batidas da lista "Ponto diário" (linhas do tipo "Entrada 07:25")
  var contar = function () {
    var re = /^(Entrada|Saída|Saida)\s*\d{1,2}:\d{2}$/i;
    var todos = Array.prototype.slice.call(document.querySelectorAll("body *")).filter(function (e) { return re.test(txt(e)); });
    return todos.filter(function (e) {
      return !Array.prototype.some.call(e.children, function (c) { return re.test(txt(c)); });
    }).length;
  };
  var esperar = async function (fn, ms) {
    for (var t = 0; t < ms; t += 500) { var v = fn(); if (v) return v; await dormir(500); }
    return null;
  };
  // definitivo = não adianta tentar de novo (sem login, já registrado, fora de ordem)
  var fim = function (ok, msg, definitivo) {
    if (ok) PontoApp.sucesso(msg);
    else if (definitivo) PontoApp.erroFinal(msg);
    else PontoApp.erro(msg);
  };

  (async function () {
    try {
      if (fase === "INICIO") {
        var tela = await esperar(function () {
          if (semLogin()) return "login";
          if (noPainel()) return "painel";
          if (naMarcacao()) return "marcacao";
          return null;
        }, 20000);
        if (tela === "login") return fim(false, "Sessão do RHID expirada. Abra o app, toque em \"Abrir RHID e fazer login\" e entre de novo.", true);
        if (!tela) return fim(false, "O painel do RHID não carregou.");
        if (tela === "marcacao") {           // abriu direto no mapa: volta ao painel para contar as batidas
          window.__rhidRodando = false;
          location.hash = "#/dashboard";
          return;
        }

        await dormir(1500);
        var n = contar();
        var ordemOk = cfg.tipo === "saida" ? n % 2 === 1 : (n % 2 === 0 && n >= 2);
        if (!ordemOk && !cfg.teste) {
          return fim(false, "Hoje já há " + n + " batida(s): a " + nomeTipo + " parece já registrada ou fora de ordem. Nada foi feito.", true);
        }
        antes = n;
        var b1 = await esperar(function () { return botoes()[0]; }, 15000);
        if (!b1) return fim(false, "Botão \"Registrar Ponto\" do painel não encontrado.");
        fase = "MARCACAO";
        PontoApp.fase(fase, n);
        b1.click();
      }

      if (fase === "MARCACAO") {
        if (!(await esperar(naMarcacao, 30000))) return fim(false, "A tela de marcação (mapa) não abriu.");
        await esperar(function () {
          return document.querySelector(".gm-style, iframe[src*='google'], img[src*='maps']");
        }, 30000);
        await dormir(4000);
        var b2 = await esperar(function () {
          var lista = botoes().filter(function (b) { return !b.disabled; });
          return lista[lista.length - 1];
        }, 30000);
        if (!b2) return fim(false, "O botão final \"Registrar Ponto\" não ficou disponível (localização bloqueada?).");

        if (cfg.teste) {
          b2.style.outline = "4px solid #ff9800";
          b2.scrollIntoView({ block: "center" });
          var aviso = antes >= 0 ? " Hoje há " + antes + " batida(s)." : "";
          return fim(true, "Teste OK: mapa carregado e botão final destacado em laranja. Nada foi registrado." + aviso);
        }

        fase = "VERIFICAR";
        PontoApp.fase(fase, -1);
        b2.click();
        await dormir(6000);
        if (!noPainel()) location.hash = "#/dashboard";
      }

      if (fase === "VERIFICAR") {
        if (!(await esperar(noPainel, 20000))) {
          location.hash = "#/dashboard";
          await esperar(noPainel, 15000);
        }
        var depois = await esperar(function () { var c = contar(); return c > antes ? c : null; }, 20000);
        return depois
          ? fim(true, "Batida confirmada no RHID (" + depois + " no dia).")
          : fim(false, "Cliquei em registrar, mas a batida não apareceu na lista. Confira no RHID.");
      }
    } catch (e) {
      fim(false, "Erro no script: " + e.message);
    }
  })();
}
