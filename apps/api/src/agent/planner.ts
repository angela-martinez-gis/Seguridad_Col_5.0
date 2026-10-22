import {
  dominioRegistrable,
  entidadPorDominioOficial,
  extraerEnlaces,
  extraerNumeros,
  hostDe,
  normalizarNumero,
  type RelatoLlamada,
  type SourceCheck,
} from "@itf/core";
import type { Config } from "../config";
import type { ReportsRepo } from "../db/reportsRepo";
import { consultarComunidad } from "../sources/comunidad";
import { revisarDominio, revisarNumero } from "../sources/entidades";
import { consultarRdap } from "../sources/rdap";
import { runSource } from "../sources/runSource";
import { consultarSafeBrowsing } from "../sources/safeBrowsing";
import { consultarUrlhaus } from "../sources/urlhaus";
import { consultarVirusTotal } from "../sources/virustotal";

export interface FuentesDeps {
  config: Config;
  reports: ReportsRepo;
}

/* Fuentes por tipo de indicador. Las usan el planificador determinista y las herramientas del agente. */

export function revisarUrl(url: string, deps: FuentesDeps): Promise<SourceCheck>[] {
  const host = hostDe(url);
  if (!host) return [];
  const t = deps.config.SOURCE_TIMEOUT_MS;
  const tareas = [
    runSource("safe_browsing", host, (s) => consultarSafeBrowsing(url, deps.config.SAFE_BROWSING_API_KEY, s), t),
    runSource("urlhaus", host, (s) => consultarUrlhaus(host, deps.config.URLHAUS_AUTH_KEY, s), t),
    runSource("virustotal", host, (s) => consultarVirusTotal(host, deps.config.VIRUSTOTAL_API_KEY, s), t),
    runSource("comunidad", host, async () => consultarComunidad(deps.reports, host, "dominio"), t),
  ];
  const local = revisarDominio(host);
  if (local) tareas.push(runSource("entidades", host, async () => local, t));
  if (!entidadPorDominioOficial(host) && !/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) {
    const dominio = dominioRegistrable(host);
    tareas.push(runSource("rdap", dominio, (s) => consultarRdap(dominio, s), t));
  }
  return tareas;
}

export function revisarRemitente(valor: string, deps: FuentesDeps): Promise<SourceCheck>[] {
  const t = deps.config.SOURCE_TIMEOUT_MS;
  const numero = normalizarNumero(valor);
  return [
    runSource("entidades", numero, async () => revisarNumero(valor), t),
    runSource("comunidad", numero, async () => consultarComunidad(deps.reports, numero, "numero"), t),
  ];
}

/**
 * Planificador determinista (research.md §2): siempre consulta las fuentes obvias
 * para cada enlace y número de la evidencia, en paralelo y con límite de tiempo.
 */
export async function planificarFuentes(
  entrada: { texto: string; numeros?: string[]; relato?: RelatoLlamada; consultarEnlaces?: boolean },
  deps: FuentesDeps,
): Promise<SourceCheck[]> {
  const urls = new Map<string, string>();
  for (const e of entrada.consultarEnlaces === false ? [] : extraerEnlaces(entrada.texto)) {
    const h = hostDe(e);
    if (h && !urls.has(h)) urls.set(h, e);
  }
  const numeros = new Set(
    [...extraerNumeros(entrada.texto), ...(entrada.numeros ?? []), entrada.relato?.numeroOrigen]
      .filter((n): n is string => !!n && n.replace(/\D/g, "").length >= 5)
      .map(normalizarNumero),
  );

  const tareas = [
    ...[...urls.values()].slice(0, 5).flatMap((u) => revisarUrl(u, deps)),
    ...[...numeros].slice(0, 5).flatMap((n) => revisarRemitente(n, deps)),
  ];
  return Promise.all(tareas);
}
