// Leitura das três fontes. Funções puras (texto/JSON da fonte -> eventos), testadas com fixtures reais
// em tests/fixtures/. A busca na rede fica no index.ts.
//   F1        Jolpica (api.jolpi.ca, sem chave)            -> sessões do fim de semana
//   Cruzeiro  football-data.org (Série A, X-Auth-Token)     -> jogos do Cruzeiro (id 1771)
//   CS2       Liquipedia (API MediaWiki, action=parse)      -> jogos de FURIA, Legacy, MIBR, Imperial e 9z

export type Esporte = "cruzeiro" | "f1" | "cs";

export interface Evento {
  id: string;
  esporte: Esporte;
  titulo: string;
  detalhe: string;
  competicao: string;
  inicio: string; // ISO UTC
  duracao_min: number;
  link: string | null;
}

const HORA = 3_600_000;

// ---------- F1 (Jolpica) ----------

const SESSOES_F1: [chave: string, nome: string, duracao: number][] = [
  ["FirstPractice", "Treino Livre 1", 60],
  ["SecondPractice", "Treino Livre 2", 60],
  ["ThirdPractice", "Treino Livre 3", 60],
  ["SprintQualifying", "Classificação Sprint", 45],
  ["Sprint", "Sprint", 60],
  ["Qualifying", "Classificação", 60],
];

const GP_PT: Record<string, string> = {
  Australian: "da Austrália", Chinese: "da China", Japanese: "do Japão", Bahrain: "do Bahrein",
  "Saudi Arabian": "da Arábia Saudita", Miami: "de Miami", "Emilia Romagna": "da Emília-Romanha",
  Monaco: "de Mônaco", Spanish: "da Espanha", "Barcelona-Catalunya": "de Barcelona", Canadian: "do Canadá",
  Austrian: "da Áustria", British: "da Grã-Bretanha", Belgian: "da Bélgica", Hungarian: "da Hungria",
  Dutch: "da Holanda", Italian: "da Itália", Madrid: "de Madri", Azerbaijan: "do Azerbaijão",
  Singapore: "de Singapura", "United States": "dos EUA", "Mexico City": "da Cidade do México",
  Brazilian: "do Brasil", "São Paulo": "de São Paulo", "Las Vegas": "de Las Vegas", Qatar: "do Catar",
  "Abu Dhabi": "de Abu Dhabi",
};

export function nomeGP(raceName: string): string {
  const m = /^(.*) Grand Prix$/.exec(raceName.trim());
  if (!m) return raceName;
  return `GP ${GP_PT[m[1]] ?? m[1]}`;
}

interface SessaoJolpica { date: string; time?: string }
interface CorridaJolpica {
  season: string; round: string; raceName: string; date: string; time?: string;
  Circuit?: { circuitName?: string; Location?: { locality?: string; country?: string } };
  [sessao: string]: unknown;
}

const instante = (s: SessaoJolpica) => `${s.date}T${s.time ?? "00:00:00Z"}`;

/** Sessões do GP em andamento/próximo e do seguinte (no máximo `quantos` fins de semana). */
export function lerF1(dados: unknown, agora: number, quantos = 2): Evento[] {
  const corridas = ((dados as any)?.MRData?.RaceTable?.Races ?? []) as CorridaJolpica[];
  if (!Array.isArray(corridas) || corridas.length === 0) throw new Error("Jolpica sem corridas");
  const futuras = corridas.filter((c) => Date.parse(instante(c)) + 3 * HORA >= agora).slice(0, quantos);
  const eventos: Evento[] = [];
  for (const c of futuras) {
    const gp = nomeGP(c.raceName);
    const local = [c.Circuit?.Location?.locality, c.Circuit?.Location?.country].filter(Boolean).join(", ");
    const link = `https://www.formula1.com/en/racing/${c.season}`;
    const sessoes: [string, string, number, SessaoJolpica][] = [];
    for (const [chave, nome, dur] of SESSOES_F1) {
      const s = c[chave] as SessaoJolpica | undefined;
      if (s?.date) sessoes.push([chave, nome, dur, s]);
    }
    sessoes.push(["Race", "Corrida", 120, { date: c.date, time: c.time }]);
    for (const [chave, nome, dur, s] of sessoes) {
      eventos.push({
        id: `f1-${c.season}-${c.round}-${chave}`,
        esporte: "f1",
        titulo: nome,
        detalhe: local,
        competicao: gp,
        inicio: new Date(Date.parse(instante(s))).toISOString(),
        duracao_min: dur,
        link,
      });
    }
  }
  return eventos;
}

// ---------- Cruzeiro (football-data.org) ----------

export const CRUZEIRO_ID = 1771;
const ABERTOS = new Set(["SCHEDULED", "TIMED", "IN_PLAY", "PAUSED", "POSTPONED", "SUSPENDED"]);

interface PartidaFD {
  id: number; utcDate: string; status: string; matchday?: number | null;
  homeTeam: { id: number; name?: string; shortName?: string };
  awayTeam: { id: number; name?: string; shortName?: string };
  competition?: { name?: string };
}

const nomeTime = (t: PartidaFD["homeTeam"]) => t.shortName || t.name || "A definir";

export function lerCruzeiro(dados: unknown): Evento[] {
  const partidas = (dados as any)?.matches as PartidaFD[] | undefined;
  if (!Array.isArray(partidas)) throw new Error("football-data sem 'matches'");
  const comp = (dados as any)?.competition?.name as string | undefined;
  return partidas
    .filter((p) => (p.homeTeam?.id === CRUZEIRO_ID || p.awayTeam?.id === CRUZEIRO_ID) && ABERTOS.has(p.status))
    .map((p) => {
      const casa = p.homeTeam.id === CRUZEIRO_ID;
      const extras = [p.matchday ? `Rodada ${p.matchday}` : "", casa ? "em casa" : "fora",
        p.status === "POSTPONED" ? "adiado" : ""].filter(Boolean);
      return {
        id: `cruzeiro-${p.id}`,
        esporte: "cruzeiro" as const,
        titulo: `${nomeTime(p.homeTeam)} x ${nomeTime(p.awayTeam)}`,
        detalhe: extras.join(" · "),
        competicao: comp === "Campeonato Brasileiro Série A" || !comp ? "Brasileirão Série A" : comp,
        inicio: new Date(Date.parse(p.utcDate)).toISOString(),
        duracao_min: 110,
        link: null,
      };
    });
}

// ---------- CS2 (Liquipedia, página Liquipedia:Matches) ----------

/** Nome exato do time na Liquipedia -> nome curto mostrado. */
export const TIMES_CS: Record<string, string> = {
  FURIA: "FURIA",
  Legacy: "Legacy",
  MIBR: "MIBR",
  "Imperial Esports": "Imperial",
  "9z Team": "9z",
};

const DURACAO_MD: Record<string, number> = { Bo1: 60, Bo2: 120, Bo3: 180, Bo5: 300 };

const desentidade = (s: string) =>
  s.replace(/&amp;/g, "&").replace(/&#95;/g, "_").replace(/&#39;/g, "'").replace(/&quot;/g, '"')
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").trim();

export function lerLiquipedia(dados: unknown, agora: number): Evento[] {
  const html = (dados as any)?.parse?.text;
  if (typeof html !== "string") throw new Error("Liquipedia sem parse.text");
  const cartoes = html.split(/<div class="match-ticker-card">/).slice(1);
  const vistos = new Set<string>();
  const eventos: Evento[] = [];
  for (const c of cartoes) {
    const ts = Number(/data-timestamp="(\d+)"/.exec(c)?.[1]);
    if (!ts || /data-finished="finished"/.test(c)) continue;
    const times = [...c.matchAll(/<div class="team-name">([^<]*)</g)].map((m) => desentidade(m[1]));
    if (!times.some((t) => t in TIMES_CS)) continue;
    const formato = desentidade(/<\/span><\/span><div>([^<]*)<\/div>/.exec(c)?.[1] ?? "");
    const md = /Bo(\d)/.exec(formato)?.[0] ?? "";
    const duracao = DURACAO_MD[md] ?? 180;
    if (ts * 1000 + duracao * 60_000 < agora) continue;
    const torneio = /match-ticker-card-tournament-name"><a href="([^"]*)"[^>]*>([^<]*)</.exec(c);
    const nomes = times.length ? times.map((t) => TIMES_CS[t] ?? t) : ["A definir", "A definir"];
    while (nomes.length < 2) nomes.push("A definir");
    const titulo = `${nomes[0]} x ${nomes[1]}`;
    const id = `cs-${ts}-${nomes.join("-").toLowerCase().replace(/[^a-z0-9-]/g, "")}`;
    if (vistos.has(id)) continue; // a página repete o mesmo jogo em abas diferentes
    vistos.add(id);
    eventos.push({
      id,
      esporte: "cs",
      titulo,
      detalhe: formato.replace(/Bo(\d)/, "MD$1").replace(/\bRound\b/, "Rodada").replace(/\bGroup\b/, "Grupo"),
      competicao: desentidade(torneio?.[2] ?? "") || "CS2",
      inicio: new Date(ts * 1000).toISOString(),
      duracao_min: duracao,
      link: torneio ? `https://liquipedia.net${desentidade(torneio[1])}` : "https://liquipedia.net/counterstrike/Liquipedia:Matches",
    });
  }
  return eventos;
}
