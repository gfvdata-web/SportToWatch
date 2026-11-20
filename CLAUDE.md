# SportToWatch: Esportes para assistir (esportes.gfvdata.com)

Agenda pessoal, numa página só (e num app Android com widget), com dia e hora em horário de Brasília do que o dono assiste: jogos do **Cruzeiro** (Série A), sessões da **Fórmula 1** (TL1, TL2, TL3, Classificação, Corrida; em fim de semana sprint, Classificação Sprint e Sprint) e jogos de **CS2** de FURIA, Legacy, MIBR, Imperial e 9z. Sucesso = abrir a página ou olhar o widget e saber o próximo evento sem procurar em outro lugar.

**Deploy:** `deploy.yml` a cada push na `main` que mexe em `docs/` ou `worker/` (secret `CLOUDFLARE_API_TOKEN`). À mão: `npx --prefix worker wrangler deploy --config worker/wrangler.jsonc`. As migrações do D1 são manuais e só com OK: `npx --prefix worker wrangler d1 migrations apply sport-to-watch --remote --config worker/wrangler.jsonc`. APK: tag `app-vX.Y.Z` dispara o `android.yml` (grava no KV `APKS`).
**Git:** commit só quando o dono pedir. Push e deploy também só com pedido.

## Regras do produto
- Horário sempre em `America/Sao_Paulo`; o D1 guarda UTC.
- Página pública, sem login. Só a rotina escreve (`POST /api/atualizar`, `Bearer TOKEN_ENVIO`).
- Fontes legítimas e com crédito na página. **Nada de raspar a HLTV** (bloqueia robôs; contornar fere os termos): o CS vem da Liquipedia, que pede User-Agent identificado, gzip e no máximo 1 `action=parse` a cada 30 s (aqui: 1 a cada 30 min a 2 h).
- Fonte que falha mantém os eventos antigos e mostra o erro no rodapé.

## Fontes (sondadas em 2026-11-20, do PC e da Cloudflare)
| Esporte | Fonte | Da Cloudflare | Observação |
|---|---|---|---|
| F1 | Jolpica `api.jolpi.ca/ergast/f1/current.json` | 200 | sem chave; mesma do BolaoF1 |
| Cruzeiro | football-data.org `competitions/BSA/matches` (time 1771) | 200 com chave | secret `FOOTBALL_DATA_TOKEN` (mesma chave do Cruzeiro_InData). A ESPN dá **403 da Cloudflare** (Akamai); TheSportsDB grátis só devolve 1 jogo |
| CS2 | Liquipedia `counterstrike/api.php?action=parse&page=Liquipedia:Matches` | 200 | do PC dá 406 sem User-Agent identificado; nomes exatos dos times em `TIMES_CS` (`fontes.ts`) |

## Arquitetura (Worker + D1 + KV, plano grátis, sem R2, **sem Cron**)
- A conta já usa os 5 Crons. A atualização é **sob demanda**: `GET /api/eventos` responde do D1 e, se a fonte venceu, busca em `ctx.waitUntil`. Validade: F1 12 h (6 h com evento em 24 h); Cruzeiro 6 h (1 h); CS 2 h (30 min); depois de erro, no máx. 30 min; nunca menos de 5 min. A trava é um `UPDATE ... WHERE tentativa_em = ?` na tabela `fontes`. A página, o widget e o painel-status (a cada 30 min) geram o tráfego.
- `worker/src/fontes.ts`: leitura pura de cada fonte (testada com fixtures). `worker/src/index.ts`: rotas (comentário do topo), validade, trava, `/app/*` do APK.
- `worker/migrations/`: `eventos` e `fontes`.
- `docs/`: página única; o JS só desenha `/api/eventos`.
- `android/`: app "Esportes" com widget 4x2 (molde do bilbao; pacote `io.github.gfvdataweb.sporttowatch`). Widget: WorkManager a cada 30 min com rede + busca ao entrar na tela; guarda o último JSON e mostra "sem conexão" se falhar. Build e testes só no Actions (`android.yml`); tag `app-vX.Y.Z` grava `sporttowatch-vX.Y.Z-buildN.apk` e `versao.json` no KV `APKS`. Antes da 1ª tag: `KV_APKS_ID` e `CERTIFICADO_SHA256` no `android.yml` e os secrets `ANDROID_KEYSTORE_B64/SENHA/ALIAS/KEY_SENHA` no GitHub.

## Testes
`scripts/check` (sem rede, sem tokens) roda no hook a cada edição e no `.githooks/pre-commit` (`git config core.hooksPath .githooks`). Node 24 (importa o `.ts` direto). A lista "Cobertura:" fica no topo do script. Fixture nova: grave a resposta real recortada em `tests/fixtures/`.

## Local
`cp worker/.dev.vars.example worker/.dev.vars` (ponha a chave da football-data), `npx --prefix worker wrangler d1 migrations apply sport-to-watch --local --config worker/wrangler.jsonc` e `preview_start sport-to-watch-local` (`.claude/launch.json`).
