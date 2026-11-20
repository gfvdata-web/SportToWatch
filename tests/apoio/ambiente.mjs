// Ambiente de mentira do Worker para os testes no Node (sem Cloudflare, sem rede, sem tokens):
// - BANCO: D1 de verdade em SQLite na memória (node:sqlite), com todas as migrações aplicadas;
// - ASSETS: eco do caminho;
// - fetch global: só responde o que o teste programou (rede real = falha).
// O Node 24 importa o .ts do Worker direto (só apaga os tipos).
import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { fileURLToPath, pathToFileURL } from "node:url";

export const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
export const lerJson = (...p) => JSON.parse(fs.readFileSync(path.join(raiz, ...p), "utf-8"));
export const lerTexto = (...p) => fs.readFileSync(path.join(raiz, ...p), "utf-8");
export const importarWorker = (rel = "index.ts") =>
  import(pathToFileURL(path.join(raiz, "worker", "src", rel)).href);

/** Adapta node:sqlite à API do D1 (prepare/bind/first/all/run/raw e batch). */
export function criarD1() {
  const db = new DatabaseSync(":memory:");
  const dir = path.join(raiz, "worker", "migrations");
  for (const f of fs.readdirSync(dir).filter((f) => f.endsWith(".sql")).sort()) {
    db.exec(fs.readFileSync(path.join(dir, f), "utf-8"));
  }
  const normal = (v) => (v === undefined ? null : typeof v === "boolean" ? Number(v) : v);
  const consultas = [];
  const preparar = (sql, args = []) => {
    const exec = () => db.prepare(sql);
    const rodar = () => {
      consultas.push(sql);
      const r = exec().run(...args);
      return { success: true, meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
    };
    return {
      bind: (...a) => preparar(sql, a.map(normal)),
      async first(coluna) {
        consultas.push(sql);
        const linha = exec().get(...args);
        if (linha === undefined) return null;
        return coluna ? (linha[coluna] ?? null) : { ...linha };
      },
      async all() {
        consultas.push(sql);
        return { results: exec().all(...args).map((l) => ({ ...l })), success: true, meta: {} };
      },
      async raw() {
        consultas.push(sql);
        return exec().all(...args).map((l) => Object.values(l));
      },
      async run() { return rodar(); },
      _rodar: rodar,
    };
  };
  return {
    db,
    consultas,
    prepare: (sql) => preparar(sql),
    /** Como no D1: tudo numa transação; um erro desfaz o lote inteiro. */
    async batch(stmts) {
      db.exec("BEGIN");
      try {
        const res = stmts.map((s) => s._rodar());
        db.exec("COMMIT");
        return res;
      } catch (e) {
        db.exec("ROLLBACK");
        throw e;
      }
    },
    async exec(sql) { db.exec(sql); return { count: 1, duration: 0 }; },
  };
}

/** fetch global programável: rotas[prefixo de URL] = (req) => Response. Sem rota = erro. */
export function instalarFetch(rotas = {}) {
  const original = globalThis.fetch;
  const chamadas = [];
  globalThis.fetch = async (entrada, init) => {
    const req = new Request(entrada, init);
    chamadas.push(req.url);
    const chave = Object.keys(rotas).find((p) => req.url.startsWith(p));
    if (!chave) throw new Error(`rede real bloqueada no teste: ${req.url}`);
    return rotas[chave](req);
  };
  return { chamadas, restaurar: () => { globalThis.fetch = original; } };
}

/** KV em memória (get com "text"/"json"/"stream", put, delete, list). */
export function criarKV() {
  const m = new Map();
  return {
    m,
    async get(k, tipo) {
      if (!m.has(k)) return null;
      const v = m.get(k);
      const t = typeof tipo === "string" ? tipo : tipo?.type;
      if (t === "json") return JSON.parse(v);
      if (t === "stream") return new Blob([v]).stream();
      return v;
    },
    async put(k, v) { m.set(k, v); },
    async delete(k) { m.delete(k); },
  };
}

export function criarEnv(extras = {}) {
  return {
    BANCO: criarD1(),
    ASSETS: { fetch: async (req) => new Response(`asset:${new URL(req.url).pathname}`) },
    APKS: criarKV(),
    TOKEN_ENVIO: "envio-teste",
    ...extras,
  };
}

export function criarCtx() {
  const pendentes = [];
  return {
    pendentes,
    waitUntil: (p) => pendentes.push(p),
    passThroughOnException() {},
    esperar: () => Promise.all(pendentes),
  };
}

/** Chama o Worker como um cliente HTTP (espera os waitUntil terminarem). */
export async function chamar(worker, env, caminho, { metodo = "GET", corpo, cabecalhos = {}, ctx = criarCtx() } = {}) {
  const body = corpo === undefined ? undefined : typeof corpo === "string" ? corpo : JSON.stringify(corpo);
  const res = await worker.default.fetch(
    new Request(`https://esportes.teste${caminho}`, { method: metodo, body, headers: cabecalhos }), env, ctx);
  await ctx.esperar();
  const texto = await res.text();
  let dados;
  try { dados = JSON.parse(texto); } catch { dados = texto; }
  return { status: res.status, dados, res };
}
