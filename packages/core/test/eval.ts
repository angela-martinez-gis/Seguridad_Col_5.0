/* Evaluación sobre el conjunto dorado (SC-002, Constitución §V).
   --mode=rules  → analyzeLocal (por defecto, se ejecuta en CI)
   --mode=agent  → POST /api/analisis contra EVAL_API_URL (proveedor real; solo eval-ia.yml) */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { analyzeLocal, redactPII, type RelatoLlamada, type Veredicto } from "../src";

interface CasoDorado {
  id: string;
  esperado: "fraude" | "legitimo";
  modalidad: string;
  texto: string;
  relato?: RelatoLlamada;
}

const RECALL_MIN = 0.9;
const ESPECIFICIDAD_MIN = 0.8;

const args = new Map(process.argv.slice(2).map((a) => a.replace(/^--/, "").split("=") as [string, string]));
const modo = args.get("mode") ?? "rules";
const apiUrl = process.env.EVAL_API_URL ?? "http://localhost:8787";
// Pausa entre análisis en modo agente, para respetar los límites por minuto de planes gratuitos (ms).
const pausaMs = Number(process.env.EVAL_PAUSA_MS ?? 0);

const ruta = fileURLToPath(new URL("./fixtures/golden.json", import.meta.url));
const casos: CasoDorado[] = JSON.parse(readFileSync(ruta, "utf8")).casos;

async function veredictoAgente(c: CasoDorado): Promise<Veredicto> {
  const clientId = crypto.randomUUID();
  const headers = { "content-type": "application/json", "x-client-id": clientId };
  const consent = await fetch(`${apiUrl}/api/consentimientos`, {
    method: "POST",
    headers,
    body: JSON.stringify({ alcance: "analisis_ia", textoVersion: "eval" }),
  }).then((r) => r.json());
  const res = await fetch(`${apiUrl}/api/analisis`, {
    method: "POST",
    headers,
    body: JSON.stringify({
      consentId: consent.consentId,
      tipo: c.relato ? "relato_llamada" : "texto",
      texto: redactPII(c.texto).texto,
      relato: c.relato,
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} en ${c.id}`);
  return res.json();
}

async function main() {
  let vp = 0, fn = 0, vn = 0, fp = 0;
  const fallos: string[] = [];
  const porModalidad: Record<string, { total: number; ok: number }> = {};
  const iaUsada = { si: 0, no: 0 };

  for (const c of casos) {
    const v =
      modo === "agent"
        ? await veredictoAgente(c)
        : analyzeLocal({ tipo: c.relato ? "relato_llamada" : "texto", texto: c.texto, relato: c.relato });
    if (modo === "agent") {
      if (v.ia.estado === "usada") iaUsada.si++;
      else iaUsada.no++;
    }
    if (modo === "agent" && pausaMs) await new Promise((r) => setTimeout(r, pausaMs));
    const predFraude = v.nivel !== "bajo";
    const ok = (c.esperado === "fraude") === predFraude;
    if (c.esperado === "fraude") {
      if (predFraude) vp++;
      else fn++;
    } else if (predFraude) fp++;
    else vn++;
    const m = (porModalidad[c.modalidad] ??= { total: 0, ok: 0 });
    m.total++;
    if (ok) m.ok++;
    else fallos.push(`${c.id} esperado=${c.esperado} nivel=${v.nivel} puntaje=${v.puntaje}`);
  }

  const recall = vp / (vp + fn);
  const especificidad = vn / (vn + fp);
  const resultado = {
    modo,
    total: casos.length,
    recallFraude: Number(recall.toFixed(3)),
    especificidad: Number(especificidad.toFixed(3)),
    porModalidad,
    ...(modo === "agent" ? { iaUsada } : {}),
    fallos,
  };
  console.log(JSON.stringify(resultado, null, 2));

  if (recall < RECALL_MIN || especificidad < ESPECIFICIDAD_MIN) {
    console.error(`✗ Por debajo de la meta (recall ≥ ${RECALL_MIN}, especificidad ≥ ${ESPECIFICIDAD_MIN}).`);
    process.exit(1);
  }
  if (modo === "agent" && iaUsada.si === 0) {
    console.error("✗ La IA no se usó en ningún caso: revisa AI_PROVIDER y la clave.");
    process.exit(1);
  }
  console.log("✓ Conjunto dorado dentro de la meta.");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
