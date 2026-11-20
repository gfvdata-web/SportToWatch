// Página: GoatCounter, escapar, fuso, link do app, créditos e sintaxe do JS. Sem rede nem navegador.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const raiz = new URL("../docs/", import.meta.url);
const html = readFileSync(new URL("index.html", raiz), "utf8");
const jsUrl = new URL("js/app.js", raiz);
const js = readFileSync(jsUrl, "utf8");

test("GoatCounter com prefixo /SportToWatch e CSP que o libera", () => {
  assert.match(html, /"\/SportToWatch" \+ p/);
  assert.match(html, /gfvdata\.goatcounter\.com\/count/);
  assert.match(html, /script-src[^"]*https:\/\/gc\.zgo\.at/);
});
test("JS usa escapar nos dados da API", () => {
  assert.match(js, /function escapar\(/);
  assert.ok((js.match(/escapar\(/g) || []).length > 10);
});
test("horários em America/Sao_Paulo", () => {
  assert.match(js, /FUSO = "America\/Sao_Paulo"/);
  assert.match(js, /timeZone: FUSO/);
});
test("link do app Android e créditos", () => {
  assert.match(html, /href="\/app\/"/);
  assert.match(html, /liquipedia\.net/);
  assert.match(html, /CC-BY-SA 3\.0/);
  assert.match(html, /api\.jolpi\.ca/);
  assert.match(html, /football-data\.org/);
});
test("links externos abrem com rel=noopener", () => {
  for (const m of html.matchAll(/<a [^>]*target="_blank"[^>]*>/g)) assert.match(m[0], /rel="noopener"/);
  assert.match(js, /target="_blank" rel="noopener"/);
});
test("JS passa em node --check", () => {
  execFileSync(process.execPath, ["--check", fileURLToPath(jsUrl)]);
});
