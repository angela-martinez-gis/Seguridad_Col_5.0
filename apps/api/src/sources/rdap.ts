import { FuenteNoDisponible, type ResultadoParcial } from "./runSource";

/* Antigüedad del dominio vía RDAP. Se consulta directo al registro autoritativo del dominio de primer
   nivel según el directorio de IANA (rdap.org, como intermediario, rechaza peticiones desde algunos
   proveedores de nube); rdap.org queda como último recurso. */
export const DIAS_DOMINIO_NUEVO = 30;
const USER_AGENT = "IdentificaTuFraude/0.1 (+https://github.com/angela-martinez-gis/Seguridad_Col_5.0)";
const BOOTSTRAP_URL = "https://data.iana.org/rdap/dns.json";
const BOOTSTRAP_TTL_MS = 24 * 3_600_000;

let bootstrap: { en: number; porTld: Map<string, string> } | null = null;

async function servidorRdap(tld: string, signal: AbortSignal): Promise<string | undefined> {
  if (!bootstrap || Date.now() - bootstrap.en > BOOTSTRAP_TTL_MS) {
    try {
      const res = await fetch(BOOTSTRAP_URL, { headers: { "user-agent": USER_AGENT }, signal });
      if (res.ok) {
        const data = (await res.json()) as { services: [string[], string[]][] };
        const porTld = new Map<string, string>();
        for (const [tlds, urls] of data.services) for (const t of tlds) porTld.set(t.toLowerCase(), urls[0]);
        bootstrap = { en: Date.now(), porTld };
      }
    } catch {
      /* sin directorio: se usa rdap.org */
    }
  }
  return bootstrap?.porTld.get(tld);
}

export function describirAntiguedad(dias: number): string {
  if (dias < 1) return "hoy";
  if (dias < 60) return `hace ${dias} día${dias === 1 ? "" : "s"}`;
  if (dias < 730) return `hace ${Math.round(dias / 30)} meses`;
  return `hace ${Math.floor(dias / 365)} años`;
}

export async function consultarRdap(dominio: string, signal: AbortSignal, ahora = Date.now()): Promise<ResultadoParcial> {
  const tld = dominio.split(".").pop()!.toLowerCase();
  const base = (await servidorRdap(tld, signal)) ?? "https://rdap.org/";
  const url = `${base.replace(/\/?$/, "/")}domain/${encodeURIComponent(dominio)}`;
  const res = await fetch(url, {
    headers: { accept: "application/rdap+json", "user-agent": USER_AGENT },
    redirect: "follow",
    signal,
  });
  if (res.status === 404) return { resultado: "sin_datos", detalle: "El registro de dominios no tiene datos de este dominio." };
  if (!res.ok) throw new FuenteNoDisponible(`El registro de dominios respondió ${res.status}.`);
  const data = (await res.json()) as { events?: { eventAction: string; eventDate: string }[] };
  const registro = data.events?.find((e) => e.eventAction === "registration")?.eventDate;
  if (!registro) return { resultado: "sin_datos", detalle: "El registro no publica la fecha de creación del dominio." };
  const dias = Math.floor((ahora - Date.parse(registro)) / 86_400_000);
  if (dias < DIAS_DOMINIO_NUEVO) {
    return {
      resultado: "senal",
      detalle: `El dominio se registró ${describirAntiguedad(dias)}: los sitios falsos suelen ser muy recientes.`,
    };
  }
  return { resultado: "limpio", detalle: `El dominio se registró ${describirAntiguedad(dias)}.` };
}

/** Solo para pruebas. */
export function reiniciarBootstrapRdap() {
  bootstrap = null;
}
