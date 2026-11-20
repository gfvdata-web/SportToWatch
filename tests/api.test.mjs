// API com D1 em SQLite na memória e rede programada (tests/apoio/ambiente.mjs).
import { test } from "node:test";
import assert from "node:assert/strict";
import { chamar, criarEnv, importarWorker, instalarFetch, lerTexto } from "./apoio/ambiente.mjs";

const worker = await importarWorker();
const AGORA = Date.UTC(2026, 10, 20, 12);
const fixture = (n) => () => new Response(lerTexto("tests", "fixtures", n), { headers: { "content-type": "application/json" } });
const REDE = {
  "https://api.jolpi.ca/": fixture("jolpica-2026.json"),
  "https://api.football-data.org/": (req) => req.headers.get("x-auth-token") === "fd-teste"
    ? fixture("footballdata-bsa.json")() : new Response("{}", { status: 403 }),
  "https://liquipedia.net/": fixture("liquipedia-matches.json"),
};

function comRelogio(ms, fn) {
  const original = Date.now;
  Date.now = () => ms;
  return Promise.resolve().then(fn).finally(() => { Date.now = original; });
}

test("primeira visita: responde vazio na hora e busca as 3 fontes em segundo plano", () => comRelogio(AGORA, async () => {
  const env = criarEnv({ FOOTBALL_DATA_TOKEN: "fd-teste" });
  const rede = instalarFetch(REDE);
  try {
    const r1 = await chamar(worker, env, "/api/eventos");
    assert.equal(r1.status, 200);
    assert.deepEqual(r1.dados.eventos, []);
    assert.equal(rede.chamadas.length, 3);
    assert.match(rede.chamadas.find((u) => u.includes("liquipedia")), /action=parse/);

    const r2 = await chamar(worker, env, "/api/eventos");
    assert.equal(rede.chamadas.length, 3, "dado fresco não busca de novo");
    const titulos = r2.dados.eventos.map((e) => e.titulo);
    assert.ok(titulos.includes("Chapecoense x Cruzeiro"));
    assert.ok(titulos.includes("HEROIC x Imperial"));
    assert.ok(titulos.includes("Classificação"));
    const ids = r2.dados.eventos.map((e) => e.id);
    assert.ok(!ids.includes("f1-2026-21-FirstPractice"), "sessão que já acabou some");
    assert.ok(ids.includes("f1-2026-21-ThirdPractice"));
    const inicios = r2.dados.eventos.map((e) => e.inicio);
    assert.deepEqual(inicios, [...inicios].sort());
    assert.ok(r2.dados.fontes.every((f) => f.ok));
  } finally { rede.restaurar(); }
}));

test("evento em andamento vem marcado ao_vivo", () => comRelogio(AGORA, async () => {
  const env = criarEnv({ FOOTBALL_DATA_TOKEN: "fd-teste" });
  const rede = instalarFetch(REDE);
  try {
    await chamar(worker, env, "/api/eventos");
    await comRelogio(Date.UTC(2026, 10, 22, 0, 30), async () => {
      const r = await chamar(worker, env, "/api/eventos");
      const jogo = r.dados.eventos.find((e) => e.titulo === "Chapecoense x Cruzeiro");
      assert.equal(jogo.ao_vivo, true);
    });
  } finally { rede.restaurar(); }
}));

test("dado vencido busca de novo; fonte com erro mantém os eventos antigos", () => comRelogio(AGORA, async () => {
  const env = criarEnv({ FOOTBALL_DATA_TOKEN: "fd-teste" });
  let rede = instalarFetch(REDE);
  await chamar(worker, env, "/api/eventos");
  rede.restaurar();
  rede = instalarFetch({ ...REDE, "https://liquipedia.net/": () => new Response("x", { status: 429 }) });
  try {
    await comRelogio(AGORA + 3 * 3600_000, async () => {
      await chamar(worker, env, "/api/eventos");
      assert.ok(rede.chamadas.some((u) => u.includes("liquipedia")), "CS venceu (2 h) e foi buscado");
      assert.ok(!rede.chamadas.some((u) => u.includes("jolpi")), "F1 ainda vale (6 h com GP perto)");
      const r = await chamar(worker, env, "/api/eventos");
      const cs = r.dados.fontes.find((f) => f.nome === "cs");
      assert.equal(cs.ok, false);
      assert.match(cs.erro, /429/);
      assert.ok(r.dados.eventos.some((e) => e.titulo === "HEROIC x Imperial"));
    });
  } finally { rede.restaurar(); }
}));

test("sem a chave da football-data o Cruzeiro fica com erro, as outras fontes seguem", () => comRelogio(AGORA, async () => {
  const env = criarEnv();
  const rede = instalarFetch(REDE);
  try {
    await chamar(worker, env, "/api/eventos");
    const r = await chamar(worker, env, "/api/eventos");
    const cru = r.dados.fontes.find((f) => f.nome === "cruzeiro");
    assert.equal(cru.ok, false);
    assert.match(cru.erro, /FOOTBALL_DATA_TOKEN/);
    assert.ok(r.dados.eventos.some((e) => e.esporte === "f1"));
  } finally { rede.restaurar(); }
}));

test("validade: mais curta quando há evento nas próximas 24 h", async () => {
  const { validade } = worker;
  const perto = [{ esporte: "cs", inicio: new Date(AGORA + 3600_000).toISOString() }];
  assert.equal(validade("cs", perto, AGORA), 30 * 60_000);
  assert.equal(validade("cs", [], AGORA), 2 * 3600_000);
  assert.equal(validade("cruzeiro", [], AGORA), 6 * 3600_000);
});

test("POST /api/atualizar exige o token e respeita o intervalo mínimo", () => comRelogio(AGORA, async () => {
  const env = criarEnv({ FOOTBALL_DATA_TOKEN: "fd-teste" });
  const rede = instalarFetch(REDE);
  try {
    assert.equal((await chamar(worker, env, "/api/atualizar", { metodo: "POST" })).status, 401);
    assert.equal((await chamar(worker, env, "/api/atualizar", { metodo: "POST",
      cabecalhos: { authorization: "Bearer errado" } })).status, 401);
    const auth = { authorization: "Bearer envio-teste" };
    const r = await chamar(worker, env, "/api/atualizar?fonte=cs", { metodo: "POST", cabecalhos: auth });
    assert.equal(r.status, 200);
    assert.equal(r.dados.fontes.cs, "ok (2)");
    const r2 = await chamar(worker, env, "/api/atualizar?fonte=cs", { metodo: "POST", cabecalhos: auth });
    assert.match(r2.dados.fontes.cs, /ignorada/);
    assert.equal(rede.chamadas.length, 1);
    assert.equal((await chamar(worker, env, "/api/atualizar?fonte=xx", { metodo: "POST", cabecalhos: auth })).status, 400);
  } finally { rede.restaurar(); }
}));

test("estático vai para ASSETS; rota desconhecida dá 404", async () => {
  assert.equal((await chamar(worker, criarEnv(), "/index.html")).dados, "asset:/index.html");
  assert.equal((await chamar(worker, criarEnv(), "/api/nada")).status, 404);
});

test("/app: versao.json, 302 para o APK atual e nome fora do padrão dá 404", async () => {
  const env = criarEnv();
  assert.equal((await chamar(worker, env, "/app/")).status, 404);
  await env.APKS.put("versao.json", JSON.stringify({ versao: "1.0.0", apk: "sporttowatch-v1.0.0-build3.apk" }));
  await env.APKS.put("sporttowatch-v1.0.0-build3.apk", "APK");
  const r = await chamar(worker, env, "/app/");
  assert.equal(r.status, 302);
  assert.equal(r.res.headers.get("location"), "/app/sporttowatch-v1.0.0-build3.apk");
  assert.equal((await chamar(worker, env, "/app/versao.json")).dados.versao, "1.0.0");
  const apk = await chamar(worker, env, "/app/sporttowatch-v1.0.0-build3.apk");
  assert.equal(apk.dados, "APK");
  assert.match(apk.res.headers.get("content-type"), /android/);
  assert.equal((await chamar(worker, env, "/app/segredo.apk")).status, 404);
  assert.equal((await chamar(worker, env, "/app/", { metodo: "POST" })).status, 405);
});
