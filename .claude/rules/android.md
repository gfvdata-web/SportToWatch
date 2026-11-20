---
paths:
  - "android/**"
  - ".github/workflows/android.yml"
---

# Regras do app Android (`android/`)

Mesmo molde do `bilbao/android` e do `painel-status/android`.

- **O build oficial é o do Actions** (`android.yml`: testes → lint → APK). Não há JDK/SDK local: mudança no app só está pronta com a **run verde** (`gh run watch`).
- **O app só lê** `GET /api/eventos` e `app/versao.json`, ambos de `esportes.gfvdata.com`. Leitor tolerante (`data/Eventos.kt`): ignora campos desconhecidos, descarta evento sem `titulo`/`inicio` válido, `link` nulo é válido, esporte desconhecido vira "Outros". Formato do `/api/eventos` só muda de forma **aditiva**; campo novo → atualizar `Eventos.kt` e a resposta de exemplo em `app/src/test/.../apoio/Apoio.kt`.
- **Horários sempre em America/Sao_Paulo** (`ui/Formatacao.kt`); "ao vivo" e "encerrado" também pelo relógio (`data/Selecao.kt`), para o dado salvo sem conexão não mentir. Só se abre link `https://`.
- **Widget = RemoteViews** (`widget/`): desenha o último dado guardado (`ArmazemPreferencias`); quem busca é o `AtualizadorWorker` (30 min, widget novo, boot). O toque abre o app.
- **Versões só em `gradle/libs.versions.toml`**, as mesmas do Painel de Status/bilbao. Uma atualização por commit, com run verde. Wrapper: só jar oficial + `distributionSha256Sum`.
- **Lint e avisos do Kotlin barram o build**: recurso/import não usado e API depreciada são erro. `@Suppress` só com comentário.
- Textos de tela em `res/values/strings.xml` (pt-BR). Teste junto com o código (JVM em `app/src/test`, Robolectric para tela/widget, MockWebServer para rede; nada de emulador nem internet).
- **Identidade:** `applicationId` `io.github.gfvdataweb.sporttowatch` nunca muda; debug usa `.debug` e o nome "Esportes (debug)".
- **`versionCode` = `github.run_number`** do `android.yml`: nunca fixar à mão; não renomear nem recriar o workflow.
- **Versão oficial = tag `app-vX.Y.Z`** num commit com run verde (só com pedido do dono). O job `publicar` grava no KV `APKS` do Worker `sport-to-watch` (secret `CLOUDFLARE_API_TOKEN`). No topo do job: `KV_APKS_ID` (id do namespace, o mesmo de `worker/wrangler.jsonc`; o job falha enquanto for `PREENCHER_AO_CRIAR`) e `CERTIFICADO_SHA256` (`PREENCHER` na 1ª tag: o passo só imprime o valor; depois fixar). Nunca trocar a chave nem o nome do APK (`sporttowatch-vX.Y.Z-buildN.apk` é lido pelo aviso de versão).
- Secrets do GitHub: `CLOUDFLARE_API_TOKEN`; `ANDROID_KEYSTORE_B64`/`ANDROID_KEYSTORE_SENHA`/`ANDROID_KEY_ALIAS`/`ANDROID_KEY_SENHA` (chave própria deste app; backup só com o dono, fora do git).
- Segredos fora do app e do Git: keystore e senhas só nos secrets do repositório.
