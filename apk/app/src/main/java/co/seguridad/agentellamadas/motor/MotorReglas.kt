package co.seguridad.agentellamadas.motor

import java.text.Normalizer

enum class Categoria(val etiqueta: String) {
    FRAUDE("Fraude"),
    SPAM("Spam"),
    LEGITIMO("Legítimo"),
    INDETERMINADO("Sin determinar"),
}

enum class Tipo(val categoria: Categoria, val etiqueta: String) {
    BANCARIO(Categoria.FRAUDE, "Suplantación bancaria"),
    FAMILIAR(Categoria.FRAUDE, "Falso familiar en apuros"),
    PREMIO(Categoria.FRAUDE, "Falso premio o sorteo"),
    AUTORIDAD(Categoria.FRAUDE, "Suplantación de autoridad"),
    SOPORTE(Categoria.FRAUDE, "Falso soporte técnico / robo de cuenta"),
    FRAUDE_GENERAL(Categoria.FRAUDE, "Intento de fraude"),
    TELEMERCADEO(Categoria.SPAM, "Telemercadeo"),
    ROBOT(Categoria.SPAM, "Llamada automática (robot)"),
    ENCUESTA(Categoria.SPAM, "Encuesta no solicitada"),
    FINANCIERO(Categoria.SPAM, "Oferta financiera"),
    TRABAJO(Categoria.LEGITIMO, "Asunto de trabajo"),
    PERSONAL(Categoria.LEGITIMO, "Familiar o personal"),
    SERVICIO(Categoria.LEGITIMO, "Entrega o servicio"),
}

/**
 * Una señal es un grupo de frases equivalentes con un peso.
 * Una frase que termina en `*` coincide como prefijo ("transfer*" → "transferencia", "transfiera" no).
 */
data class Senal(val frases: List<String>, val tipo: Tipo, val peso: Int, val motivo: String)

data class Coincidencia(val frase: String, val senal: Senal)

data class Clasificacion(
    val categoria: Categoria,
    val tipo: Tipo?,
    val confianza: Int,
    val puntajes: Map<Categoria, Int>,
    val coincidencias: List<Coincidencia>,
    val forzada: Boolean = false,
) {
    val motivos: List<String> get() = motivosDe(coincidencias)

    /** Solo las señales que respaldan la categoría final (con una ruta forzada puede no haber ninguna). */
    val motivosDelVeredicto: List<String> get() = motivosDe(coincidencias.filter { it.senal.tipo.categoria == categoria })

    private fun motivosDe(c: List<Coincidencia>) = c.sortedByDescending { it.senal.peso }.map { it.senal.motivo }.distinct()
}

object MotorReglas {

    /** Umbrales: el fraude tiene prioridad aunque haya señales comerciales. */
    private const val UMBRAL_FRAUDE = 4
    private const val UMBRAL_SPAM = 3
    private const val UMBRAL_LEGITIMO = 2

    private val negaciones = setOf("no", "nunca", "ni", "sin", "jamas")

    private val senales = listOf(
        // ── Fraude: suplantación bancaria
        Senal(listOf("movimiento sospechoso", "compra sospechosa", "transaccion sospechosa"), Tipo.BANCARIO, 3, "Alerta falsa de movimiento sospechoso"),
        Senal(listOf("banco", "entidad bancaria", "area de seguridad"), Tipo.BANCARIO, 1, "Dice llamar de un banco"),
        Senal(listOf("bloque*", "suspend*"), Tipo.FRAUDE_GENERAL, 2, "Amenaza con bloquear o suspender"),
        Senal(listOf("su cuenta", "sus cuentas"), Tipo.BANCARIO, 1, "Menciona su cuenta"),
        Senal(listOf("digitos", "numero de la tarjeta", "numero de su tarjeta"), Tipo.BANCARIO, 3, "Pide los dígitos de la tarjeta"),
        Senal(listOf("tarjeta"), Tipo.BANCARIO, 1, "Menciona la tarjeta"),
        Senal(listOf("clave", "contrasena", "pin", "cvv", "codigo de seguridad"), Tipo.BANCARIO, 4, "Pide claves o códigos de seguridad"),
        Senal(listOf("codigo que le llego", "codigo que le va a llegar", "codigo de verificacion", "le va a llegar un codigo", "dictarme el codigo", "digame el codigo"), Tipo.SOPORTE, 4, "Pide un código que llegó por mensaje"),
        Senal(listOf("confirme sus datos", "confirmar sus datos", "confirme los", "validar su identidad", "validar sus datos", "actualizar sus datos"), Tipo.BANCARIO, 3, "Pide confirmar datos personales"),
        // ── Fraude: dinero y presión
        Senal(listOf("transfer*", "consign*", "giro", "nequi", "daviplata", "recarga"), Tipo.FRAUDE_GENERAL, 2, "Pide enviar dinero"),
        Senal(listOf("urgente", "inmediatamente", "de inmediato", "hoy mismo", "ya mismo"), Tipo.FRAUDE_GENERAL, 1, "Crea sensación de urgencia"),
        Senal(listOf("no le diga a nadie", "no le cuente", "no le digas", "no le diga a mi"), Tipo.FAMILIAR, 3, "Pide guardar el secreto"),
        // ── Fraude: falso familiar
        Senal(listOf("adivine quien", "adivina quien", "sabe quien habla", "no me reconoce"), Tipo.FAMILIAR, 3, "Hace adivinar su identidad"),
        Senal(listOf("cambie de numero", "numero nuevo", "este es mi nuevo numero"), Tipo.FAMILIAR, 2, "Dice tener un número nuevo"),
        Senal(listOf("estoy en problemas", "tuve un accidente", "me detuvieron", "estoy detenido", "me tienen retenido"), Tipo.FAMILIAR, 3, "Emergencia familiar sin verificar"),
        // ── Fraude: falso premio
        Senal(listOf("se gano", "usted gano", "ganador", "felicitaciones"), Tipo.PREMIO, 3, "Anuncia un premio inesperado"),
        Senal(listOf("sorteo", "rifa"), Tipo.PREMIO, 2, "Menciona un sorteo"),
        Senal(listOf("costo de envio", "pagar el envio", "impuesto del premio", "para reclamar"), Tipo.PREMIO, 3, "Cobra por entregar un premio"),
        // ── Fraude: suplantación de autoridad
        Senal(listOf("dian", "fiscalia", "policia", "juzgado", "sijin"), Tipo.AUTORIDAD, 1, "Dice ser una autoridad"),
        Senal(listOf("embargo", "orden de captura", "proceso en su contra", "demanda en su contra"), Tipo.AUTORIDAD, 3, "Amenaza con medidas legales"),
        Senal(listOf("deuda pendiente", "multa", "si no paga"), Tipo.AUTORIDAD, 2, "Exige pagar una deuda"),
        Senal(listOf("le envio un enlace", "le mando un link", "le envio un link", "ingrese al enlace"), Tipo.FRAUDE_GENERAL, 3, "Envía un enlace de pago"),
        // ── Fraude: falso soporte técnico
        Senal(listOf("soporte tecnico", "servicio tecnico"), Tipo.SOPORTE, 2, "Dice ser soporte técnico"),
        Senal(listOf("anydesk", "teamviewer", "acceso remoto", "descargue la aplicacion", "instale la aplicacion"), Tipo.SOPORTE, 4, "Pide instalar control remoto"),
        Senal(listOf("su linea presenta", "falla en su linea"), Tipo.SOPORTE, 1, "Reporta una falla inventada"),

        // ── Spam: telemercadeo
        Senal(listOf("oferta", "promocion", "descuento"), Tipo.TELEMERCADEO, 2, "Oferta comercial"),
        Senal(listOf("plan", "gigas", "minutos ilimitados"), Tipo.TELEMERCADEO, 1, "Ofrece un plan"),
        Senal(listOf("cambio de operador", "cambio de compania", "portabilidad", "cambiarse de operador"), Tipo.TELEMERCADEO, 2, "Busca cambio de operador"),
        Senal(listOf("tiempo limitado", "solo por hoy", "solo le tomara", "es rapido"), Tipo.TELEMERCADEO, 2, "Presión comercial"),
        Senal(listOf("gratis", "sin costo"), Tipo.TELEMERCADEO, 1, "Promete algo gratis"),
        // ── Spam: robot
        Senal(listOf("llamada automatica", "mensaje grabado", "esta es una grabacion"), Tipo.ROBOT, 3, "Llamada grabada o automática"),
        Senal(listOf("marque uno", "marque 1", "oprima", "presione"), Tipo.ROBOT, 3, "Menú automático de opciones"),
        Senal(listOf("beneficio"), Tipo.ROBOT, 1, "Promete un beneficio genérico"),
        // ── Spam: encuesta
        Senal(listOf("encuesta", "estudio de mercado"), Tipo.ENCUESTA, 3, "Encuesta no solicitada"),
        Senal(listOf("preguntas cortas", "unas preguntas", "minutos de su tiempo"), Tipo.ENCUESTA, 2, "Pide tiempo para preguntas"),
        // ── Spam: oferta financiera
        Senal(listOf("preaprobad*", "libre inversion", "tasa especial", "cupo aprobado"), Tipo.FINANCIERO, 3, "Ofrece crédito preaprobado"),
        Senal(listOf("asesor le envia", "le enviamos la informacion", "por correo"), Tipo.FINANCIERO, 1, "Ofrece enviar publicidad"),

        // ── Legítimo: trabajo
        Senal(listOf("oficina", "trabajo", "empresa donde"), Tipo.TRABAJO, 2, "Contexto laboral"),
        Senal(listOf("reunion", "reporte", "proyecto", "informe"), Tipo.TRABAJO, 2, "Tema de trabajo concreto"),
        // ── Legítimo: personal
        Senal(listOf("mama", "papa", "hermana", "hermano", "abuela", "amiga", "amigo"), Tipo.PERSONAL, 1, "Relación personal"),
        Senal(listOf("almuerzo", "cena", "cumpleanos", "domingo", "visita"), Tipo.PERSONAL, 2, "Plan personal"),
        // ── Legítimo: servicio
        Senal(listOf("mensajeria", "domicilio", "paquete", "pedido"), Tipo.SERVICIO, 2, "Entrega o domicilio"),
        Senal(listOf("entregar", "recibir el", "reciba el"), Tipo.SERVICIO, 2, "Coordina una entrega"),
        // ── Legítimo: sin presión
        Senal(listOf("no es urgente", "no hay afan", "tranquilo"), Tipo.PERSONAL, 2, "No presiona"),
        Senal(listOf("devuelva la llamada", "me llame", "cuando pueda", "cuando se desocupe", "deje una nota", "le deja razon"), Tipo.PERSONAL, 2, "Acepta dejar el mensaje"),
    )

    private val patrones: List<Pair<Senal, List<Pair<String, Regex>>>> = senales.map { senal ->
        senal to senal.frases.map { frase ->
            val prefijo = frase.endsWith("*")
            val base = Regex.escape(normalizar(frase.removeSuffix("*")))
            frase to Regex("(?<![a-z0-9])$base" + if (prefijo) "" else "(?![a-z0-9])")
        }
    }

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun clasificar(texto: String): Clasificacion {
        val norm = normalizar(texto)
        val coincidencias = patrones.mapNotNull { (senal, frases) ->
            frases.firstNotNullOfOrNull { (frase, regex) ->
                regex.findAll(norm).firstOrNull { !negada(norm, it.range.first) }?.let { Coincidencia(frase.removeSuffix("*"), senal) }
            }
        }
        val puntajes = Categoria.entries.associateWith { cat ->
            coincidencias.filter { it.senal.tipo.categoria == cat }.sumOf { it.senal.peso }
        }
        val fraude = puntajes.getValue(Categoria.FRAUDE)
        val spam = puntajes.getValue(Categoria.SPAM)
        val legitimo = puntajes.getValue(Categoria.LEGITIMO)

        val categoria = when {
            fraude >= UMBRAL_FRAUDE -> Categoria.FRAUDE
            spam >= UMBRAL_SPAM && spam >= legitimo -> Categoria.SPAM
            legitimo >= UMBRAL_LEGITIMO && legitimo > spam && legitimo > fraude -> Categoria.LEGITIMO
            else -> Categoria.INDETERMINADO
        }
        val tipo = coincidencias
            .filter { it.senal.tipo.categoria == categoria }
            .groupBy { it.senal.tipo }
            .mapValues { (_, c) -> c.sumOf { it.senal.peso } }
            // Un tipo específico gana a "Intento de fraude" en caso de empate.
            .maxWithOrNull(compareBy({ it.value }, { it.key != Tipo.FRAUDE_GENERAL }))?.key

        val confianza = if (categoria == Categoria.INDETERMINADO) {
            40
        } else {
            val propio = puntajes.getValue(categoria)
            val otro = puntajes.filterKeys { it != categoria }.values.max()
            (60 + (propio - otro) * 4).coerceIn(55, 98)
        }
        return Clasificacion(categoria, tipo, confianza, puntajes, coincidencias)
    }

    /** "no es urgente": la frase se ignora si una negación aparece en las 3 palabras anteriores. */
    private fun negada(texto: String, inicio: Int): Boolean =
        texto.substring(0, inicio).trim().split(" ").takeLast(3).any { it in negaciones }

    fun tipoPorDefecto(categoria: Categoria): Tipo? = when (categoria) {
        Categoria.FRAUDE -> Tipo.FRAUDE_GENERAL
        Categoria.SPAM -> Tipo.TELEMERCADEO
        Categoria.LEGITIMO -> Tipo.PERSONAL
        Categoria.INDETERMINADO -> null
    }

    /** Modo Director: impone la categoría conservando las señales detectadas. */
    fun forzar(c: Clasificacion, categoria: Categoria): Clasificacion =
        if (c.categoria == categoria) {
            c.copy(forzada = true)
        } else {
            c.copy(categoria = categoria, tipo = tipoPorDefecto(categoria), confianza = 95, forzada = true)
        }
}
