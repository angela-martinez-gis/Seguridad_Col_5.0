import {
  BANDA_MIN,
  categoriasDe,
  nivelDesdePuntos,
  puntajeDesdePuntos,
  queHacerPara,
  sumarPuntos,
} from "./analyzeLocal";
import { ENTIDADES } from "./entidades";
import { canalesDenuncia } from "./guidance";
import { NIVEL_ORDEN, maxNivel, type AiAssessment, type IaEstado, type Motivo, type SourceCheck, type Veredicto } from "./types";

const NOMBRE_FUENTE: Record<SourceCheck["fuente"], string> = {
  safe_browsing: "Google Safe Browsing",
  urlhaus: "URLhaus (abuse.ch)",
  virustotal: "VirusTotal",
  rdap: "Registro de dominios (RDAP)",
  entidades: "Catálogo de entidades oficiales",
  comunidad: "Reportes de la comunidad",
};

const PUNTOS_FUENTE: Record<SourceCheck["fuente"], number> = {
  safe_browsing: 5,
  urlhaus: 5,
  virustotal: 5,
  rdap: 2,
  entidades: 2,
  comunidad: 3,
};

export const nombreFuente = (f: SourceCheck["fuente"]) => NOMBRE_FUENTE[f];

/** Convierte resultados de fuentes abiertas en motivos citables (FR-005). */
export function motivosDeFuentes(fuentes: SourceCheck[]): Motivo[] {
  return fuentes
    .filter((f) => f.estado === "ok" && (f.resultado === "coincide" || f.resultado === "senal"))
    .map((f) => ({
      texto: f.detalle,
      origen: "fuente" as const,
      categoria: "fuente_abierta" as const,
      puntos: PUNTOS_FUENTE[f.fuente],
      fuente: NOMBRE_FUENTE[f.fuente],
      consultadoEn: f.consultadoEn,
    }));
}

const palabras = (t: string) =>
  new Set(
    t
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .split(/\W+/)
      .filter((w) => w.length > 3),
  );

function similar(a: string, b: string): boolean {
  const pa = palabras(a);
  const pb = palabras(b);
  if (!pa.size || !pb.size) return false;
  const inter = [...pa].filter((w) => pb.has(w)).length;
  return inter / Math.min(pa.size, pb.size) >= 0.6;
}

export interface InfoIa {
  estado: IaEstado;
  proveedor?: string;
  modelo?: string;
}

const etiqueta = { bajo: "bajo", medio: "medio", alto: "alto" } as const;

/**
 * Fusiona el veredicto de reglas con fuentes abiertas y la evaluación de IA (FR-006):
 * el nivel final es el más alto, y si reglas/fuentes e IA difieren se explica.
 */
export function mergeVerdict(local: Veredicto, ai: AiAssessment | null, fuentes: SourceCheck[], ia: InfoIa): Veredicto {
  const motivosBase = [...local.motivos.filter((m) => m.origen === "regla"), ...motivosDeFuentes(fuentes)];
  const puntos = sumarPuntos(motivosBase);
  const nivelBase = nivelDesdePuntos(puntos);

  let nivel = nivelBase;
  let discrepancia: string | undefined;
  const motivos = [...motivosBase];
  let puntaje = puntajeDesdePuntos(puntos);

  if (ai) {
    nivel = maxNivel(nivelBase, ai.nivel);
    if (ai.nivel !== nivelBase) {
      const masAlto = NIVEL_ORDEN[ai.nivel] > NIVEL_ORDEN[nivelBase] ? "el análisis de IA" : "las reglas y fuentes";
      discrepancia = `Las reglas y fuentes estimaron riesgo ${etiqueta[nivelBase]} y el análisis de IA estimó riesgo ${etiqueta[ai.nivel]}. Se muestra el más alto (${masAlto}) por precaución.`;
    }
    for (const m of ai.motivos) {
      if (motivos.some((x) => x.categoria === m.categoria && similar(x.texto, m.texto))) continue;
      motivos.push({ texto: m.texto, origen: "ia", categoria: m.categoria });
    }
    if (NIVEL_ORDEN[nivel] > NIVEL_ORDEN[nivelBase]) {
      puntaje = Math.max(puntaje, BANDA_MIN[nivel] + Math.round(ai.confianza * 25));
    }
  }

  const entidadNombre = ai?.entidadSuplantada || local.entidad;
  const entidad = entidadNombre
    ? ENTIDADES.find((e) => e.nombre.toLowerCase() === entidadNombre.toLowerCase()) ??
      ENTIDADES.find((e) => entidadNombre.toLowerCase().includes(e.alias[0]))
    : undefined;

  const modalidad =
    ai && ai.modalidad !== "otro" && !(ai.modalidad === "ninguna" && nivel !== "bajo")
      ? ai.modalidad
      : nivel === "bajo"
        ? "ninguna"
        : local.modalidad === "ninguna"
          ? "otro"
          : local.modalidad;

  const queHacer = queHacerPara(nivel);
  for (const p of ai?.pasosAdicionales.slice(0, 2) ?? []) if (!queHacer.some((q) => similar(q, p))) queHacer.push(p);

  return {
    ...local,
    nivel,
    puntaje: Math.min(100, puntaje),
    modalidad,
    motivos,
    categorias: categoriasDe(motivos),
    queHacer,
    denuncia: canalesDenuncia(entidad),
    fuentes,
    discrepancia,
    entidad: entidad?.nombre ?? entidadNombre ?? undefined,
    ia,
    generadoEn: new Date().toISOString(),
  };
}
