import { analyzeLocal, mergeVerdict, redactPII, SolicitudAnalisis, type InfoIa } from "@itf/core";
import type { FastifyInstance } from "fastify";
import { ejecutarAgente } from "../agent/agent";
import { planificarFuentes } from "../agent/planner";
import type { AppDeps } from "../deps";
import { HttpProblem } from "../errors";
import { requireOwner } from "../plugins/clientId";

export const limiteAnalisis = (deps: AppDeps) => ({ max: deps.config.RATE_LIMIT_ANALISIS, timeWindow: "10 minutes" });

export function registerAnalisis(app: FastifyInstance, deps: AppDeps) {
  app.post("/api/analisis", { config: { rateLimit: limiteAnalisis(deps) } }, async (req) => {
    const owner = requireOwner(req);
    const body = SolicitudAnalisis.parse(req.body);
    if (!deps.consents.consumir(body.consentId, owner, "analisis_ia")) {
      throw new HttpProblem(400, "Consentimiento inválido", "El consentimiento no existe, no es de este dispositivo o ya se usó.");
    }

    const t0 = performance.now();
    // Se vuelve a redactar en el servidor (defensa en profundidad, FR-021).
    const texto = redactPII(body.texto).texto;
    const relato = body.relato
      ? { ...body.relato, notas: redactPII(body.relato.notas).texto, quienDijoSer: redactPII(body.relato.quienDijoSer).texto }
      : undefined;
    const local = analyzeLocal({ tipo: body.tipo, texto, relato });
    const tReglas = performance.now();

    const fuentesDeps = { config: deps.config, reports: deps.reports };
    const fuentes = await planificarFuentes({ texto, numeros: body.numeros, relato, consultarEnlaces: body.consultarEnlaces }, fuentesDeps);
    const tFuentes = performance.now();

    let ia: InfoIa = { estado: "desactivada" };
    let assessment = null;
    let fuentesExtra = fuentes.slice(0, 0);
    if (deps.ai) {
      const r = await ejecutarAgente({
        provider: deps.ai,
        texto,
        relato,
        reglas: local,
        fuentes,
        deps: fuentesDeps,
        timeoutMs: deps.config.AI_TIMEOUT_MS,
        maxToolCalls: deps.config.AI_MAX_TOOL_CALLS,
      });
      assessment = r.assessment;
      fuentesExtra = r.fuentesExtra;
      ia = { estado: r.assessment ? "usada" : "no_disponible", proveedor: deps.ai.id, modelo: r.modelo ?? deps.ai.model };
      if (r.error) req.log.warn({ errorIa: r.error, detalle: r.errorDetalle, llamadas: r.llamadasHerramientas }, "IA no disponible para este análisis");
    }
    const tIa = performance.now();

    const veredicto = mergeVerdict(local, assessment, [...fuentes, ...fuentesExtra], ia);
    // Métricas de latencia sin contenido (SC-001, SC-012).
    req.log.info(
      {
        tiempos: {
          totalMs: Math.round(tIa - t0),
          reglasMs: Math.round(tReglas - t0),
          fuentesMs: Math.round(tFuentes - tReglas),
          iaMs: Math.round(tIa - tFuentes),
        },
        tipo: body.tipo,
        nivel: veredicto.nivel,
        ia: ia.estado,
      },
      "análisis completado",
    );
    return veredicto;
  });
}
