import { huellaEvidencia, Veredicto } from "@itf/core";
import { afterEach, describe, expect, it } from "vitest";
import { MockProvider } from "../../src/ai/mock";
import { analizar, crearApp, nuevoCliente, type App } from "../helpers";

let app: App;
afterEach(() => app?.close());

const TEXTO = "Ganaste un premio de $5.000.000, confirma tu clave en http://premio.click";

describe("cambio de proveedor solo por configuración (US5, SC-005)", () => {
  for (const [id, modelo] of [
    ["proveedor-a", "modelo-a"],
    ["proveedor-b", "modelo-b"],
  ]) {
    it(`${id}: mismo formato de veredicto y el caso registra proveedor/modelo`, async () => {
      app = await crearApp({}, new MockProvider("none", id, modelo));
      const c = nuevoCliente();
      const consentimiento = await app.inject({
        method: "POST",
        url: "/api/consentimientos",
        headers: { "x-client-id": c },
        payload: { alcance: "analisis_ia", textoVersion: "t" },
      });
      const consentId = consentimiento.json().consentId;
      const r = await app.inject({
        method: "POST",
        url: "/api/analisis",
        headers: { "x-client-id": c },
        payload: { consentId, tipo: "texto", texto: TEXTO },
      });
      const v = Veredicto.parse(r.json());
      expect(v.ia).toMatchObject({ estado: "usada", proveedor: id, modelo });

      const caso = await app.inject({
        method: "POST",
        url: "/api/casos",
        headers: { "x-client-id": c },
        payload: { huellaEvidencia: await huellaEvidencia(TEXTO), tipoEvidencia: "texto", veredicto: v, consentId },
      });
      expect(caso.json().caso.veredicto.ia).toMatchObject({ proveedor: id, modelo });
    });
  }

  it("la fábrica crea el adaptador compatible con OpenAI para proveedores reales", async () => {
    const { createAiProvider } = await import("../../src/ai/factory");
    const { loadConfig } = await import("../../src/config");
    for (const p of ["groq", "gemini", "openrouter"] as const) {
      const ai = createAiProvider(loadConfig({ NODE_ENV: "test", AI_PROVIDER: p, AI_API_KEY: "x", AI_MODEL: "m" }));
      expect(ai?.id).toBe(p);
      expect(ai?.model).toBe("m");
    }
  });

  it("analizar con el proveedor por defecto sigue funcionando", async () => {
    app = await crearApp();
    expect((await analizar(app, nuevoCliente(), TEXTO)).statusCode).toBe(200);
  });
});

describe("cadena de modelos de respaldo (cuota diaria por modelo)", () => {
  const respuesta = (model: string) =>
    new Response(
      JSON.stringify({ id: "x", object: "chat.completion", created: 0, model, choices: [{ index: 0, finish_reason: "stop", message: { role: "assistant", content: '{"nivel":"alto","confianza":0.8,"modalidad":"otro","motivos":[]}' } }] }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  const cuota = () =>
    new Response(JSON.stringify({ error: { message: "Rate limit reached on tokens per day (TPD). Please try again in 6m50.4s.", type: "tokens", code: "rate_limit_exceeded" } }), {
      status: 429,
      headers: { "content-type": "application/json" },
    });

  it("si el principal agota su cuota responde el de respaldo, y el principal queda en pausa", async () => {
    const { OpenAiCompatibleProvider, esperaSugeridaMs } = await import("../../src/ai/openaiCompatible");
    const pedidos: string[] = [];
    const fetchFalso = (async (_u: string, init: RequestInit) => {
      const model = JSON.parse(String(init.body)).model as string;
      pedidos.push(model);
      return model === "principal" ? cuota() : respuesta(model);
    }) as unknown as typeof fetch;
    const p = new OpenAiCompatibleProvider({ id: "groq", baseURL: "http://x/v1", apiKey: "k", model: "principal", fallbackModels: ["respaldo"], fetch: fetchFalso });
    const r1 = await p.chat({ messages: [{ role: "user", content: "hola" }] }, new AbortController().signal);
    expect(r1.model).toBe("respaldo");
    const r2 = await p.chat({ messages: [{ role: "user", content: "hola" }] }, new AbortController().signal);
    expect(r2.model).toBe("respaldo");
    expect(pedidos).toEqual(["principal", "respaldo", "respaldo"]);
    expect(esperaSugeridaMs("Please try again in 6m50.4s")).toBe(410_400);
  });

  it("si todos agotan su cuota lanza 429 (el análisis sigue con reglas)", async () => {
    const { OpenAiCompatibleProvider } = await import("../../src/ai/openaiCompatible");
    const p = new OpenAiCompatibleProvider({ id: "groq", baseURL: "http://x/v1", apiKey: "k", model: "a", fallbackModels: ["b"], fetch: (async () => cuota()) as unknown as typeof fetch });
    await expect(p.chat({ messages: [{ role: "user", content: "hola" }] }, new AbortController().signal)).rejects.toMatchObject({ status: 429 });
  });
});
