import { FuenteNoDisponible, type ResultadoParcial } from "./runSource";

/* VirusTotal API v3, reputación de un dominio (last_analysis_stats). Solo se envía el dominio,
   nunca la ruta ni la URL completa (mismo criterio que URLhaus). */
export async function consultarVirusTotal(dominio: string, apiKey: string | undefined, signal: AbortSignal): Promise<ResultadoParcial> {
  if (!apiKey) throw new FuenteNoDisponible("VirusTotal no está configurado.");
  const res = await fetch(`https://www.virustotal.com/api/v3/domains/${encodeURIComponent(dominio)}`, {
    headers: { accept: "application/json", "x-apikey": apiKey },
    signal,
  });
  if (res.status === 404) return { resultado: "sin_datos", detalle: "VirusTotal no tiene registros de este dominio." };
  if (!res.ok) throw new FuenteNoDisponible(`VirusTotal respondió ${res.status}.`);

  const data = (await res.json()) as {
    data?: { attributes?: { last_analysis_stats?: { malicious?: number; suspicious?: number; harmless?: number } } };
  };
  const stats = data.data?.attributes?.last_analysis_stats ?? {};
  const maliciosos = stats.malicious ?? 0;
  const sospechosos = stats.suspicious ?? 0;
  if (maliciosos > 0) {
    return { resultado: "coincide", detalle: `${maliciosos} motor(es) de seguridad de VirusTotal marcan este dominio como malicioso.` };
  }
  if (sospechosos > 0) {
    return { resultado: "senal", detalle: `${sospechosos} motor(es) de seguridad de VirusTotal lo marcan como sospechoso.` };
  }
  return { resultado: "limpio", detalle: "Ningún motor de seguridad de VirusTotal lo marca como peligroso." };
}
