import type { Nivel } from "@itf/core";

export const NOMBRE_MODALIDAD: Record<string, string> = {
  suplantacion_bancaria: "Suplantación de banco o billetera",
  suplantacion_autoridad: "Suplantación de autoridad",
  falso_premio: "Falso premio o subsidio",
  falso_empleo: "Falso empleo o tareas pagadas",
  falsa_inversion: "Falsa inversión",
  extorsion: "Extorsión o amenaza",
  vishing: "Llamada falsa (vishing)",
  compra_falsa: "Compra o envío falso",
  otro: "Otra modalidad de fraude",
  ninguna: "Sin modalidad de fraude identificada",
};

export const NIVEL: Record<Nivel, { titulo: string; icono: string; resumen: string }> = {
  bajo: { titulo: "Riesgo bajo", icono: "✓", resumen: "No encontramos señales claras de fraude." },
  medio: { titulo: "Riesgo medio", icono: "!", resumen: "Hay señales que justifican desconfiar." },
  alto: { titulo: "Riesgo alto", icono: "✕", resumen: "Tiene señales fuertes de fraude. No actúes sobre este mensaje." },
};

export const ORIGEN: Record<string, { nombre: string; variable: string }> = {
  regla: { nombre: "Regla", variable: "--serie-regla" },
  ia: { nombre: "IA", variable: "--serie-ia" },
  fuente: { nombre: "Fuente", variable: "--serie-fuente" },
};

export const NOMBRE_FUENTE: Record<string, string> = {
  safe_browsing: "Google Safe Browsing",
  urlhaus: "URLhaus (abuse.ch)",
  virustotal: "VirusTotal",
  rdap: "Registro de dominios (RDAP)",
  entidades: "Catálogo de entidades",
  comunidad: "Reportes de la comunidad",
};

export const fechaBogota = (iso: string) =>
  new Date(iso).toLocaleString("es-CO", { timeZone: "America/Bogota", dateStyle: "medium", timeStyle: "short" });

export const horaBogota = (iso: string) =>
  new Date(iso).toLocaleTimeString("es-CO", { timeZone: "America/Bogota", hour: "2-digit", minute: "2-digit" });

export const EJEMPLO =
  "Estimado cliente, su cuenta Bancolombia será bloqueada hoy por actividad inusual. Para desbloquearla confirme su clave y código en http://bancolombia-seguro.xyz/validar";
