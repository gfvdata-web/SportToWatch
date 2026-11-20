-- Esquema inicial do D1 sport-to-watch. Migração nova = arquivo novo numerado
-- (0002_..., 0003_...), nunca editar uma já aplicada no --remote.

-- eventos: o que a página e o widget mostram. Cada fonte troca os seus de uma vez (DELETE + INSERT num batch).
CREATE TABLE eventos (
  id            TEXT PRIMARY KEY,          -- f1-2026-21-Qualifying, cruzeiro-<id football-data>, cs-<ts>-<times>
  esporte       TEXT NOT NULL CHECK (esporte IN ('cruzeiro', 'f1', 'cs')),
  titulo        TEXT NOT NULL,             -- "Chapecoense x Cruzeiro", "Classificação", "FURIA x Legacy"
  detalhe       TEXT NOT NULL DEFAULT '',  -- rodada / local / fase e formato
  competicao    TEXT NOT NULL DEFAULT '',  -- "Brasileirão Série A", "GP de Las Vegas", "PGL Major Singapore 2026"
  inicio        TEXT NOT NULL,             -- ISO UTC
  duracao_min   INTEGER NOT NULL,          -- para saber até quando o evento está "ao vivo"
  link          TEXT,
  atualizado_em TEXT NOT NULL
);
CREATE INDEX eventos_inicio ON eventos (inicio);

-- fontes: estado de cada fonte. tentativa_em (ms) é a trava/limite de frequência da busca.
CREATE TABLE fontes (
  nome          TEXT PRIMARY KEY,
  atualizado_em TEXT,                      -- última busca que deu certo (ISO)
  tentativa_em  INTEGER NOT NULL DEFAULT 0,
  ok            INTEGER NOT NULL DEFAULT 0,
  erro          TEXT
);
INSERT INTO fontes (nome) VALUES ('cruzeiro'), ('f1'), ('cs');
