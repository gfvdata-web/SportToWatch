// Esportes para assistir (esportes.gfvdata.com): site (../docs, estáticos) e API (/api/*).
//
// Leitura pública:
//   GET  /api/eventos           próximos eventos (Cruzeiro, F1, CS2) + estado de cada fonte.
//                               Se uma fonte estiver vencida, responde na hora com o que há no D1
//                               e busca de novo em segundo plano (ctx.waitUntil).
// Rotina (Bearer TOKEN_ENVIO):
//   POST /api/atualizar?fonte=  força a busca de uma fonte (f1 | cruzeiro | cs) ou de todas.
// APK do app Android (KV APKS, gravado pelo android.yml; o Worker só lê):
//   GET  /app/versao.json                     aviso de versão do app
//   GET  /app/sporttowatch-vX.Y.Z-buildN.apk  o APK (ficam os 3 mais novos)
//   GET  /app/                                302 para o APK do versao.json
// Sem Cron: a conta já usa os 5. A atualização vem do próprio tráfego (página, widget, painel-status).
// Custo: Workers + D1 do plano grátis (param no limite em vez de cobrar). Sem R2.

import { lerCruzeiro, lerF1, lerLiquipedia, type Esporte, type Evento } from "./fontes.ts";

export interface Ambiente {
  ASSETS: Fetcher;
  BANCO: D1Database;
  APKS: KVNamespace;
  TOKEN_ENVIO: string;
  FOOTBALL_DATA_TOKEN?: string;
}

const MIN = 60_000;
const HORA = 60 * MIN;
export const FONTES: Esporte[] = ["cruzeiro", "f1", "cs"];
const USER_AGENT = "SportToWatch/1.0 (+https://esportes.gfvdata.com; agenda pessoal, poucas consultas por dia)";
/** Liquipedia: no máximo 1 action=parse a cada 30 s (termos da API). Nunca buscamos mais rápido que isto. */
const INTERVALO_MINIMO = 5 * MIN;

export default {
  async fetch(request, env, ctx): Promise<Response> {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/app" || url.pathname.startsWith("/app/")) return await app(request, env, url.pathname);
      if (!url.pathname.startsWith("/api/")) return env.ASSETS.fetch(request);
      return await api(request, env, url, ctx);
    } catch (e) {
      if (e instanceof RespostaErro) return erro(e.status, e.message);
      console.error(e);
      return erro(500, "erro interno");
    }
  },
} satisfies ExportedHandler<Ambiente>;

class RespostaErro extends Error {
  status: number;
  constructor(status: number, msg: string) { super(msg); this.status = status; }
}
const falha = (status: number, msg: string): never => { throw new RespostaErro(status, msg); };
const erro = (status: number, msg: string) => Response.json({ erro: msg }, { status });
const json = (dados: unknown) => Response.json(dados, {
  headers: { "cache-control": "no-store", "access-control-allow-origin": "*" },
});

async function api(request: Request, env: Ambiente, url: URL, ctx: ExecutionContext): Promise<Response> {
  const p = url.pathname.replace(/\/+$/, "");
  const m = request.method;
  const agora = Date.now();
  if (m === "GET" && p === "/api/eventos") {
    const resposta = await montarResposta(env, agora);
    for (const f of await fontesVencidas(env, agora, resposta.eventos)) ctx.waitUntil(atualizar(env, f, agora));
    return json(resposta);
  }
  if (p === "/api/atualizar") {
    if (m !== "POST") falha(405, "use POST");
    if (!(await tokenValido(request, env))) falha(401, "token inválido");
    const pedida = url.searchParams.get("fonte");
    if (pedida && !FONTES.includes(pedida as Esporte)) falha(400, "fonte desconhecida");
    const alvo = pedida ? [pedida as Esporte] : FONTES;
    const feitas: Record<string, string> = {};
    for (const f of alvo) {
      const pegou = await reservar(env, f, agora, INTERVALO_MINIMO);
      feitas[f] = pegou ? await atualizar(env, f, agora) : "ignorada (busca recente)";
    }
    return json({ ok: true, fontes: feitas });
  }
  return falha(404, "rota desconhecida");
}

// ---------- resposta ----------

interface LinhaFonte { nome: string; atualizado_em: string | null; tentativa_em: number; ok: number; erro: string | null }

export async function montarResposta(env: Ambiente, agora: number) {
  const corte = new Date(agora).toISOString();
  const { results } = await env.BANCO.prepare(
    `SELECT id, esporte, titulo, detalhe, competicao, inicio, duracao_min, link FROM eventos
     WHERE datetime(inicio, '+' || duracao_min || ' minutes') >= datetime(?) ORDER BY inicio, id`,
  ).bind(corte).all<Evento>();
  const fontes = (await env.BANCO.prepare(
    "SELECT nome, atualizado_em, ok, erro FROM fontes ORDER BY nome").all<LinhaFonte>()).results;
  return {
    agora: corte,
    eventos: results.map((e) => ({ ...e, ao_vivo: Date.parse(e.inicio) <= agora })),
    fontes: fontes.map((f) => ({ nome: f.nome, atualizado_em: f.atualizado_em, ok: !!f.ok, erro: f.erro })),
  };
}

// ---------- quando buscar de novo ----------

/** Validade do que está no D1: mais curta quando há evento da fonte nas próximas 24 h. */
export function validade(fonte: Esporte, eventos: { esporte: string; inicio: string }[], agora: number): number {
  const perto = eventos.some((e) => e.esporte === fonte && Date.parse(e.inicio) - agora < 24 * HORA);
  if (fonte === "f1") return perto ? 6 * HORA : 12 * HORA;
  if (fonte === "cruzeiro") return perto ? 1 * HORA : 6 * HORA;
  return perto ? 30 * MIN : 2 * HORA;
}

async function fontesVencidas(env: Ambiente, agora: number, eventos: Evento[]): Promise<Esporte[]> {
  const vencidas: Esporte[] = [];
  for (const f of FONTES) {
    if (await reservar(env, f, agora, validade(f, eventos, agora))) vencidas.push(f);
  }
  return vencidas;
}

/**
 * Trava + limite de frequência no D1: só uma requisição "ganha" a busca de uma fonte (UPDATE condicional).
 * Depois de um erro, tenta de novo em no máximo 30 min.
 */
export async function reservar(env: Ambiente, fonte: Esporte, agora: number, intervalo: number): Promise<boolean> {
  const linha = await env.BANCO.prepare("SELECT tentativa_em, ok FROM fontes WHERE nome = ?").bind(fonte).first<LinhaFonte>();
  if (!linha) return false;
  const espera = linha.ok ? intervalo : Math.min(intervalo, 30 * MIN);
  if (agora - linha.tentativa_em < Math.max(espera, INTERVALO_MINIMO)) return false;
  const r = await env.BANCO.prepare("UPDATE fontes SET tentativa_em = ? WHERE nome = ? AND tentativa_em = ?")
    .bind(agora, fonte, linha.tentativa_em).run();
  return r.meta.changes === 1;
}

// ---------- busca ----------

async function buscarJson(url: string, cabecalhos: Record<string, string> = {}): Promise<unknown> {
  const r = await fetch(url, { headers: { "user-agent": USER_AGENT, accept: "application/json", ...cabecalhos } });
  if (!r.ok) throw new Error(`HTTP ${r.status} em ${new URL(url).host}`);
  return r.json();
}

export async function buscar(env: Ambiente, fonte: Esporte, agora: number): Promise<Evento[]> {
  if (fonte === "f1") return lerF1(await buscarJson("https://api.jolpi.ca/ergast/f1/current.json"), agora);
  if (fonte === "cruzeiro") {
    if (!env.FOOTBALL_DATA_TOKEN) throw new Error("secret FOOTBALL_DATA_TOKEN ausente");
    return lerCruzeiro(await buscarJson(
      "https://api.football-data.org/v4/competitions/BSA/matches?status=SCHEDULED,TIMED,IN_PLAY,PAUSED,POSTPONED",
      { "x-auth-token": env.FOOTBALL_DATA_TOKEN }));
  }
  return lerLiquipedia(await buscarJson(
    "https://liquipedia.net/counterstrike/api.php?action=parse&page=Liquipedia:Matches&prop=text&format=json&formatversion=2",
    { "accept-encoding": "gzip" }), agora);
}

/** Busca, troca os eventos da fonte de uma vez (batch) e registra o resultado. Erro mantém os dados antigos. */
export async function atualizar(env: Ambiente, fonte: Esporte, agora: number): Promise<string> {
  const quando = new Date(agora).toISOString();
  try {
    const eventos = await buscar(env, fonte, agora);
    const ins = env.BANCO.prepare(
      `INSERT OR REPLACE INTO eventos (id, esporte, titulo, detalhe, competicao, inicio, duracao_min, link, atualizado_em)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`);
    await env.BANCO.batch([
      env.BANCO.prepare("DELETE FROM eventos WHERE esporte = ?").bind(fonte),
      ...eventos.map((e) => ins.bind(e.id, e.esporte, e.titulo, e.detalhe, e.competicao, e.inicio, e.duracao_min, e.link, quando)),
      env.BANCO.prepare("UPDATE fontes SET atualizado_em = ?, ok = 1, erro = NULL WHERE nome = ?").bind(quando, fonte),
    ]);
    return `ok (${eventos.length})`;
  } catch (e) {
    const msg = String((e as Error)?.message ?? e).slice(0, 200);
    console.error(`fonte ${fonte}: ${msg}`);
    await env.BANCO.prepare("UPDATE fontes SET ok = 0, erro = ? WHERE nome = ?").bind(msg, fonte).run();
    return `erro: ${msg}`;
  }
}

// ---------- token da rotina ----------

async function tokenValido(request: Request, env: Ambiente): Promise<boolean> {
  const enviado = (request.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!enviado || !env.TOKEN_ENVIO) return false;
  const enc = new TextEncoder();
  const [a, b] = await Promise.all([enviado, env.TOKEN_ENVIO].map((s) => crypto.subtle.digest("SHA-256", enc.encode(s))));
  const x = new Uint8Array(a), y = new Uint8Array(b);
  let dif = 0;
  for (let i = 0; i < x.length; i++) dif |= x[i] ^ y[i];
  return dif === 0;
}

// ---------- APK (só leitura do KV APKS; molde do bilbao) ----------

const VERSAO = "versao.json";
/** Mesmo padrão que o android.yml grava e o app lê. */
export const RE_APK = /^sporttowatch-v(\d+\.\d+\.\d+)-build(\d+)\.apk$/;

async function app(request: Request, env: Ambiente, caminho: string): Promise<Response> {
  if (!["GET", "HEAD"].includes(request.method)) return erro(405, "método não aceito");
  const nome = caminho.replace(/^\/app\/?/, "");
  if (nome === "" || nome === VERSAO) {
    const texto = await env.APKS.get(VERSAO, "text");
    if (texto === null) return erro(404, "nenhuma versão publicada");
    if (nome === "") {
      let apk = "";
      try { apk = String((JSON.parse(texto) as { apk?: unknown }).apk ?? ""); } catch { /* cai no 404 */ }
      if (!RE_APK.test(apk)) return erro(404, "nenhuma versão publicada");
      return new Response(null, { status: 302, headers: { location: `/app/${apk}`, "cache-control": "no-store" } });
    }
    return new Response(request.method === "HEAD" ? null : texto, {
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
    });
  }
  if (!RE_APK.test(nome)) return erro(404, "não encontrado");
  const apk = await env.APKS.get(nome, "stream");
  if (apk === null) return erro(404, "não encontrado");
  const headers = new Headers({
    "content-type": "application/vnd.android.package-archive",
    "content-disposition": `attachment; filename="${nome}"`,
    "cache-control": "public, max-age=31536000, immutable", // nome traz versão e build: nunca muda
  });
  if (request.method === "HEAD") { await apk.cancel(); return new Response(null, { headers }); }
  return new Response(apk, { headers });
}
