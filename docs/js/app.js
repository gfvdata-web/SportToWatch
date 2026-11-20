// Agenda de esportes: só lê /api/eventos e desenha. Horários sempre em America/Sao_Paulo.
const FUSO = "America/Sao_Paulo";
const RECARGA_MS = 5 * 60 * 1000;
const NOVA_TENTATIVA_MS = 5000;
const MAX_TENTATIVAS = 3;
const SECOES = { cruzeiro: "lista-cruzeiro", f1: "lista-f1", cs: "lista-cs" };

const fmtDia = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, weekday: "short", day: "2-digit", month: "2-digit" });
const fmtHora = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, hour: "2-digit", minute: "2-digit", hour12: false });
const fmtChave = new Intl.DateTimeFormat("en-CA", { timeZone: FUSO, year: "numeric", month: "2-digit", day: "2-digit" });

function escapar(v) {
  return String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function dataHora(iso) {
  const d = new Date(iso);
  return fmtDia.format(d).replace(".", "").replace(",", "") + " · " + fmtHora.format(d);
}
// "hoje" / "amanhã" / "" conforme o dia em Brasília.
function rotuloDia(iso, agora) {
  const dia = (x) => Date.parse(fmtChave.format(x) + "T00:00:00Z");
  const dif = Math.round((dia(new Date(iso)) - dia(agora)) / 86400000);
  return dif === 0 ? "hoje" : dif === 1 ? "amanhã" : "";
}
function link(e) {
  return e.link ? ` <a href="${escapar(e.link)}" target="_blank" rel="noopener" title="Abrir a fonte" aria-label="Abrir a fonte">↗</a>` : "";
}
function vivo(e) { return e.ao_vivo ? '<span class="vivo">AO VIVO</span>' : ""; }

function cartao(e, agora) {
  const rot = rotuloDia(e.inicio, agora);
  return `<div class="cartao ${escapar(e.esporte)}">${vivo(e)}<b>${escapar(e.titulo)}</b>` +
    `<span class="quando">${rot ? escapar(rot) + " · " : ""}${escapar(dataHora(e.inicio))}</span>` +
    `<span class="detalhe">${escapar(e.esporte === "cruzeiro" ? e.detalhe : e.competicao)}</span></div>`;
}
function linha(e) {
  return `<div class="linha"><span class="quando">${vivo(e)}${escapar(dataHora(e.inicio))}</span>` +
    `<span><b>${escapar(e.titulo)}</b>${link(e)}<br><span class="detalhe">${escapar(e.detalhe)}</span></span></div>`;
}
function secao(lista, agrupar) {
  if (!lista.length) return '<p class="vazio">Nenhum evento marcado</p>';
  if (!agrupar) return lista.map(linha).join("");
  const grupos = new Map();
  for (const e of lista) {
    if (!grupos.has(e.competicao)) grupos.set(e.competicao, []);
    grupos.get(e.competicao).push(e);
  }
  return [...grupos].map(([nome, itens]) => `<div class="grupo"><h3>${escapar(nome)}</h3>${itens.map(linha).join("")}</div>`).join("");
}
function atualizadoHa(iso, agora) {
  if (!iso) return "ainda sem leitura";
  const min = Math.max(0, Math.round((agora - new Date(iso)) / 60000));
  if (min < 1) return "atualizado agora";
  if (min < 60) return `atualizado há ${min} min`;
  const h = Math.round(min / 60);
  return h < 48 ? `atualizado há ${h} h` : `atualizado há ${Math.round(h / 24)} dias`;
}

function desenhar(dados) {
  const agora = new Date(dados.agora || Date.now());
  const eventos = dados.eventos || [];
  const limite = agora.getTime() + 48 * 3600 * 1000;
  const prox = eventos.filter((e) => e.ao_vivo || new Date(e.inicio).getTime() <= limite);
  document.getElementById("faixa").innerHTML = prox.length ? prox.map((e) => cartao(e, agora)).join("") : '<p class="vazio">Nada nas próximas 48 h</p>';
  for (const [esp, id] of Object.entries(SECOES)) {
    document.getElementById(id).innerHTML = secao(eventos.filter((e) => e.esporte === esp), esp !== "cruzeiro");
  }
  document.getElementById("fontes").innerHTML = (dados.fontes || []).map((f) =>
    `<li class="${f.ok ? "" : "erro"}">${escapar(f.nome)}: ${escapar(atualizadoHa(f.atualizado_em, agora))}` +
    `${f.ok ? "" : " (" + escapar(f.erro || "erro") + ")"}</li>`).join("");
}

async function carregar(tentativa = 0) {
  const aviso = document.getElementById("aviso");
  try {
    const r = await fetch("/api/eventos", { cache: "no-store" });
    if (!r.ok) throw new Error("HTTP " + r.status);
    const dados = await r.json();
    desenhar(dados);
    if (!(dados.eventos || []).length && tentativa < MAX_TENTATIVAS) {
      aviso.textContent = "Buscando os horários…";
      setTimeout(() => carregar(tentativa + 1), NOVA_TENTATIVA_MS);
    } else aviso.textContent = "";
  } catch (e) {
    aviso.textContent = "Não consegui carregar a agenda agora.";
  }
}

function iniciarTema() {
  const botao = document.getElementById("tema");
  const escuro = () => {
    const t = document.documentElement.getAttribute("data-theme");
    return t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
  };
  const rotular = () => { botao.textContent = escuro() ? "Tema claro" : "Tema escuro"; };
  botao.addEventListener("click", () => {
    const novo = escuro() ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", novo);
    try { localStorage.setItem("tema", novo); } catch (e) { /* sem armazenamento */ }
    rotular();
  });
  rotular();
}

iniciarTema();
carregar();
setInterval(() => carregar(), RECARGA_MS);
