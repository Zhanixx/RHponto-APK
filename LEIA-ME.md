# Ponto Almoço RHID (Android)

Registra a saída (12:00) e a volta (13:00) do almoço no RHID, usando a localização real do celular,
**somente quando o celular está desbloqueado e em uso**. Se estiver bloqueado, espera você usar
por até 30 min (configurável); depois disso avisa para registrar manualmente.

## Gerar o APK

**Opção A — GitHub (sem instalar nada):**
1. Crie um repositório privado no GitHub e envie esta pasta inteira.
2. Aba **Actions** → "Gerar APK" roda sozinho. Ao terminar, baixe o artefato `PontoAlmocoRHID-apk`.

**Opção B — Android Studio:** File → Open → esta pasta → Build → Build APK(s).

## Primeira configuração no celular
1. Instale o APK (permita "instalar apps desconhecidos").
2. No app, toque nos botões 1 a 4 da seção "Configuração inicial" até todos ficarem ✔.
3. Toque em **5. Abrir RHID e fazer login** e entre na sua conta.
4. Use **Testar saída** — percorre o fluxo e só destaca o botão final em laranja, sem registrar.

Em Xiaomi/Samsung/Motorola, libere também "Início automático" / "Sem restrições" de bateria para o app,
senão o sistema pode matar os alarmes.
