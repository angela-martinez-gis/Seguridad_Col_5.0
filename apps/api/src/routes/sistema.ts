import { VERSION_REGLAS } from "@itf/core";
import type { FastifyInstance } from "fastify";
import { TEXTO_CONSENTIMIENTO_VERSION, type AppDeps } from "../deps";

export function registerSistema(app: FastifyInstance, deps: AppDeps) {
  // Sin límite de uso: la consultan el keepalive, el healthcheck y la PWA al abrir.
  app.get("/api/salud", { config: { rateLimit: false } }, async () => ({ ok: true, version: deps.config.APP_VERSION }));

  app.get("/api/config", { config: { rateLimit: false } }, async () => ({
    ia: {
      proveedor: deps.ai?.id ?? "none",
      modelo: deps.ai?.model ?? null,
      modelosRespaldo: deps.ai?.models?.slice(1) ?? [],
      disponible: !!deps.ai,
      transcripcion: !!deps.ai?.transcribe && !!deps.ai?.sttModel,
    },
    fuentes: {
      safeBrowsing: !!deps.config.SAFE_BROWSING_API_KEY,
      urlhaus: !!deps.config.URLHAUS_AUTH_KEY,
      rdap: true,
      entidades: true,
      comunidad: true,
    },
    versionReglas: VERSION_REGLAS,
    textoConsentimientoVersion: TEXTO_CONSENTIMIENTO_VERSION,
    registroEfimero: true,
  }));
}
