// Leitura das fontes com respostas reais gravadas em tests/fixtures/ (Jolpica, football-data, Liquipedia).
import { test } from "node:test";
import assert from "node:assert/strict";
import { importarWorker, lerJson } from "./apoio/ambiente.mjs";

const f = await importarWorker("fontes.ts");
const AGORA = Date.UTC(2026, 10, 20, 12); // sex 20/11/2026 9h em Brasília

test("F1: as 5 sessões do GP de Las Vegas e do seguinte, em UTC", () => {
  const ev = f.lerF1(lerJson("tests", "fixtures", "jolpica-2026.json"), AGORA);
  assert.equal(ev.length, 10);
  const vegas = ev.filter((e) => e.competicao === "GP de Las Vegas");
  assert.deepEqual(vegas.map((e) => e.titulo),
    ["Treino Livre 1", "Treino Livre 2", "Treino Livre 3", "Classificação", "Corrida"]);
  assert.equal(vegas.at(-1).inicio, "2026-11-22T04:00:00.000Z");
  assert.equal(ev[5].competicao, "GP do Catar");
});

test("F1: corrida que já acabou sai; GP passa a ser o próximo", () => {
  const depois = Date.UTC(2026, 10, 22, 8);
  const ev = f.lerF1(lerJson("tests", "fixtures", "jolpica-2026.json"), depois);
  assert.equal(ev[0].competicao, "GP do Catar");
  assert.equal(ev.at(-1).competicao, "GP de Abu Dhabi");
});

test("F1: fim de semana sprint traz Classificação Sprint e Sprint, sem TL2/TL3", () => {
  const dados = { MRData: { RaceTable: { Races: [{
    season: "2026", round: "5", raceName: "Miami Grand Prix", date: "2026-05-03", time: "20:00:00Z",
    FirstPractice: { date: "2026-05-01", time: "16:30:00Z" },
    SprintQualifying: { date: "2026-05-01", time: "20:30:00Z" },
    Sprint: { date: "2026-05-02", time: "16:00:00Z" },
    Qualifying: { date: "2026-05-02", time: "20:00:00Z" },
  }] } } };
  const ev = f.lerF1(dados, Date.UTC(2026, 3, 30));
  assert.deepEqual(ev.map((e) => e.titulo),
    ["Treino Livre 1", "Classificação Sprint", "Sprint", "Classificação", "Corrida"]);
  assert.equal(ev[0].competicao, "GP de Miami");
});

test("F1: resposta sem corridas é erro (mantém os dados antigos)", () => {
  assert.throws(() => f.lerF1({ MRData: { RaceTable: { Races: [] } } }, AGORA));
});

test("Cruzeiro: só os jogos do Cruzeiro, com mando e rodada", () => {
  const ev = f.lerCruzeiro(lerJson("tests", "fixtures", "footballdata-bsa.json"));
  assert.deepEqual(ev.map((e) => e.titulo),
    ["Chapecoense x Cruzeiro", "Fluminense x Cruzeiro", "Cruzeiro x Internacional"]);
  assert.equal(ev[0].detalhe, "Rodada 36 · fora");
  assert.equal(ev[2].detalhe, "Rodada 38 · em casa");
  assert.equal(ev[0].inicio, "2026-11-22T00:00:00.000Z");
  assert.equal(ev[0].competicao, "Brasileirão Série A");
});

test("Cruzeiro: jogo encerrado não entra", () => {
  const d = lerJson("tests", "fixtures", "footballdata-bsa.json");
  d.matches.find((m) => m.awayTeam.id === 1771).status = "FINISHED";
  assert.equal(f.lerCruzeiro(d).length, 2);
});

test("CS2: só jogos não encerrados dos 5 times, com nome curto e formato", () => {
  const ev = f.lerLiquipedia(lerJson("tests", "fixtures", "liquipedia-matches.json"), AGORA);
  assert.deepEqual(ev.map((e) => e.titulo), ["HEROIC x Imperial", "9z x Lynn Vision Gaming"]);
  assert.equal(ev[0].competicao, "PGL Major Singapore 2026");
  assert.equal(ev[0].detalhe, "Rodada 1 - MD1");
  assert.equal(ev[0].duracao_min, 60);
  assert.match(ev[0].link, /^https:\/\/liquipedia\.net\/counterstrike\/PGL\/2026\/Singapore/);
  assert.ok(ev.every((e) => e.esporte === "cs"));
});

test("CS2: nome parecido (FURIA fe, Legacy Academy) não entra; jogo repetido conta uma vez", () => {
  const cartao = (time, ts) => `<div class="match-ticker-card"><span class="timer-object" data-timestamp="${ts}">x</span></span><div>Final - Bo3</div>` +
    `<div class="team-name">${time}</div><div class="team-name">NAVI</div>` +
    `<span class="match-ticker-card-tournament-name"><a href="/counterstrike/X" title="X">Copa X</a></span></div>`;
  const ts = AGORA / 1000 + 3600;
  const html = cartao("FURIA fe", ts) + cartao("Legacy Academy", ts) + cartao("MIBR", ts) + cartao("MIBR", ts);
  const ev = f.lerLiquipedia({ parse: { text: html } }, AGORA);
  assert.deepEqual(ev.map((e) => e.titulo), ["MIBR x NAVI"]);
  assert.equal(ev[0].detalhe, "Final - MD3");
});
