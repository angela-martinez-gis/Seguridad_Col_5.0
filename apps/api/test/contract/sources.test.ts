import { createHash } from "node:crypto";
import { describe, expect, it, vi } from "vitest";
import { consultarRdap, reiniciarBootstrapRdap } from "../../src/sources/rdap";
import { runSource } from "../../src/sources/runSource";
import { consultarSafeBrowsing, expresionesUrl } from "../../src/sources/safeBrowsing";
import { consultarUrlhaus } from "../../src/sources/urlhaus";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const señal = () => new AbortController().signal;

describe("Google Safe Browsing v5 (prefijos de hash)", () => {
  it("no envía la URL en claro y detecta coincidencias", async () => {
    const url = "http://phish.example.xyz/login/paso";
    const completo = createHash("sha256").update("phish.example.xyz/login/paso").digest("base64");
    const fetchMock = vi.fn(async (_u: string | URL) => json({ fullHashes: [{ fullHash: completo, fullHashDetails: [{ threatType: "SOCIAL_ENGINEERING" }] }] }));
    vi.stubGlobal("fetch", fetchMock);
    const r = await consultarSafeBrowsing(url, "k", señal());
    expect(r.resultado).toBe("coincide");
    expect(r.detalle).toContain("phishing");
    const llamada = String(fetchMock.mock.calls[0][0]);
    expect(llamada).not.toContain("phish.example");
    expect(llamada).toContain("hashPrefixes=");
  });

  it("sin coincidencias → limpio", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json({})));
    expect((await consultarSafeBrowsing("https://ok.com", "k", señal())).resultado).toBe("limpio");
  });

  it("genera expresiones de host y ruta sin query", () => {
    const e = expresionesUrl("http://a.b.c.example.com/1/2/3.html?x=1");
    expect(e).toContain("a.b.c.example.com/1/2/3.html");
    expect(e).toContain("example.com/");
    expect(e.some((x) => x.includes("?"))).toBe(false);
  });
});

describe("URLhaus", () => {
  it("coincide cuando hay URLs registradas", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json({ query_status: "ok", url_count: "3" })));
    expect((await consultarUrlhaus("mal.xyz", "k", señal())).resultado).toBe("coincide");
  });
  it("no_results → limpio", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json({ query_status: "no_results" })));
    expect((await consultarUrlhaus("ok.com", "k", señal())).resultado).toBe("limpio");
  });
});

describe("RDAP", () => {
  it("consulta el registro autoritativo del TLD según IANA, con User-Agent", async () => {
    reiniciarBootstrapRdap();
    const pedidos: { url: string; ua: string | null }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (u: string, init?: RequestInit) => {
        pedidos.push({ url: String(u), ua: new Headers(init?.headers).get("user-agent") });
        if (String(u).includes("data.iana.org")) return json({ services: [[["xyz"], ["https://rdap.centralnic.com/xyz/"]]] });
        return json({ events: [{ eventAction: "registration", eventDate: "2001-01-01T00:00:00Z" }] });
      }),
    );
    await consultarRdap("ejemplo.xyz", señal());
    expect(pedidos[1].url).toBe("https://rdap.centralnic.com/xyz/domain/ejemplo.xyz");
    expect(pedidos[1].ua).toContain("IdentificaTuFraude");
  });

  it("dominio de 6 días → señal", async () => {
    const ahora = Date.parse("2026-10-20T00:00:00Z");
    vi.stubGlobal("fetch", vi.fn(async () => json({ events: [{ eventAction: "registration", eventDate: "2026-10-14T00:00:00Z" }] })));
    const r = await consultarRdap("nuevo.xyz", señal(), ahora);
    expect(r.resultado).toBe("senal");
    expect(r.detalle).toContain("6 días");
  });
  it("dominio antiguo → limpio", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => json({ events: [{ eventAction: "registration", eventDate: "2001-01-01T00:00:00Z" }] })));
    expect((await consultarRdap("viejo.com", señal())).resultado).toBe("limpio");
  });
});

describe("runSource: las fuentes caídas no detienen el análisis (FR-012)", () => {
  it("sin clave → no_disponible", async () => {
    const r = await runSource("urlhaus", "x.com", (s) => consultarUrlhaus("x.com", undefined, s));
    expect(r.estado).toBe("no_disponible");
    expect(r.detalle).toContain("no está configurado");
  });
  it("error de red → no_disponible", async () => {
    const r = await runSource("rdap", "x.com", (s) => consultarRdap("x.com", s));
    expect(r.estado).toBe("no_disponible");
  });
  it("lentitud → timeout", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((_u: string, init: RequestInit) => new Promise((_r, rej) => init.signal?.addEventListener("abort", () => rej(new Error("abort"))))),
    );
    const r = await runSource("rdap", "x.com", (s) => consultarRdap("x.com", s), 200);
    expect(r.estado).toBe("timeout");
  });
});
