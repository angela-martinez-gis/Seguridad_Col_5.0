package co.seguridad.agentellamadas.datos

import co.seguridad.agentellamadas.motor.Categoria

/**
 * Guion de una llamada de demostración. En modo automático las respuestas del emisor
 * se reproducen con una voz distinta a la del asistente; en modo en vivo solo se usan
 * el número y la ubicación, y las respuestas las dice una persona al micrófono.
 */
data class Escenario(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val esperado: Categoria,
    val numero: String,
    val ubicacion: String,
    val respuesta1: String,
    val respuesta2: String,
)

object Escenarios {

    /** Nombre del dueño del teléfono, al que llaman en los casos legítimos. */
    const val TITULAR = "Laura"

    val todos = listOf(
        Escenario(
            id = "fraude-banco",
            titulo = "Falso banco",
            descripcion = "Alerta de compra sospechosa y pide datos de la tarjeta",
            esperado = Categoria.FRAUDE,
            numero = "+57 601 744 8210",
            ubicacion = "Bogotá, Colombia",
            respuesta1 = "Le llamamos del área de seguridad de su banco. Detectamos un movimiento sospechoso en su cuenta y la vamos a bloquear.",
            respuesta2 = "Mi código es el cuatro cuatro siete uno. Para cancelar la compra necesito que me confirme los dígitos de su tarjeta y el código que le llegó por mensaje.",
        ),
        Escenario(
            id = "fraude-familiar",
            titulo = "Falso familiar en apuros",
            descripcion = "\"Adivine quién soy\": pide plata urgente y en secreto",
            esperado = Categoria.FRAUDE,
            numero = "+57 312 845 0937",
            ubicacion = "Colombia",
            respuesta1 = "Hola tío, ¿adivine quién soy? Es que cambié de número y estoy en problemas.",
            respuesta2 = "Soy su sobrino, tío. Tuve un accidente y necesito que me haga una transferencia urgente por Nequi, pero no le diga a nadie.",
        ),
        Escenario(
            id = "fraude-premio",
            titulo = "Falso premio",
            descripcion = "Anuncia una moto ganada y cobra el envío",
            esperado = Categoria.FRAUDE,
            numero = "+57 300 112 4589",
            ubicacion = "Colombia",
            respuesta1 = "¡Felicitaciones! Usted se ganó una moto en el sorteo de aniversario de nuestra cadena de almacenes.",
            respuesta2 = "Somos Almacenes La Economía. Solo debe pagar el costo de envío de ochenta mil pesos por Nequi hoy mismo para reclamar su premio.",
        ),
        Escenario(
            id = "fraude-autoridad",
            titulo = "Suplantación de la DIAN",
            descripcion = "Amenaza con embargo y envía un enlace de pago",
            esperado = Categoria.FRAUDE,
            numero = "+57 601 307 8800",
            ubicacion = "Bogotá, Colombia",
            respuesta1 = "Le hablamos de la DIAN. Usted tiene una deuda pendiente y hay un proceso de embargo en su contra.",
            respuesta2 = "Si no paga hoy se emite la orden. Le envío un enlace para que haga el pago inmediatamente y evite el embargo.",
        ),
        Escenario(
            id = "fraude-soporte",
            titulo = "Falso soporte técnico",
            descripcion = "Pide el código SMS e instalar control remoto",
            esperado = Categoria.FRAUDE,
            numero = "+1 202 555 0187",
            ubicacion = "Estados Unidos",
            respuesta1 = "Buenas, le llamo del soporte técnico de su operador. Su línea presenta una falla y la vamos a suspender.",
            respuesta2 = "Para reactivarla le va a llegar un código por mensaje de texto, dígame el código por favor. También descargue la aplicación AnyDesk.",
        ),
        Escenario(
            id = "spam-telemercadeo",
            titulo = "Telemercadeo de telefonía",
            descripcion = "Promoción de plan móvil con cambio de operador",
            esperado = Categoria.SPAM,
            numero = "+57 601 489 3300",
            ubicacion = "Bogotá, Colombia",
            respuesta1 = "Buenas tardes, le llamamos para ofrecerle una promoción exclusiva en su plan de telefonía móvil, con cincuenta gigas.",
            respuesta2 = "Es una oferta por tiempo limitado. Si hace el cambio de operador hoy le damos el primer mes gratis, solo le tomará dos minutos.",
        ),
        Escenario(
            id = "spam-robot",
            titulo = "Llamada robot",
            descripcion = "Mensaje grabado con menú de opciones",
            esperado = Categoria.SPAM,
            numero = "+57 601 555 0199",
            ubicacion = "Bogotá, Colombia",
            respuesta1 = "Esta es una llamada automática. Tenemos un beneficio especial para usted. Marque uno para hablar con un asesor.",
            respuesta2 = "Esta es una llamada automática. Oprima uno para recibir su beneficio, o dos para no volver a recibir llamadas.",
        ),
        Escenario(
            id = "spam-encuesta",
            titulo = "Encuesta comercial",
            descripcion = "Encuesta de satisfacción no solicitada",
            esperado = Categoria.SPAM,
            numero = "+57 604 322 1180",
            ubicacion = "Medellín, Colombia",
            respuesta1 = "Hola, buenas tardes. Estamos realizando una encuesta de satisfacción sobre servicios de televisión en su sector.",
            respuesta2 = "Son solo cinco preguntas cortas, no le quitará más de tres minutos de su tiempo.",
        ),
        Escenario(
            id = "spam-credito",
            titulo = "Crédito preaprobado",
            descripcion = "Oferta financiera masiva, sin pedir datos",
            esperado = Categoria.SPAM,
            numero = "+57 601 742 0055",
            ubicacion = "Bogotá, Colombia",
            respuesta1 = "Le llamamos porque tiene un crédito de libre inversión preaprobado con una tasa especial.",
            respuesta2 = "Antes de eso, déjeme contarle: si le interesa, un asesor le envía la información de la oferta por correo, sin papeleo.",
        ),
        Escenario(
            id = "legitimo-oficina",
            titulo = "Compañero de oficina",
            descripcion = "Pregunta por un reporte de trabajo",
            esperado = Categoria.LEGITIMO,
            numero = "+57 315 774 2201",
            ubicacion = "Colombia",
            respuesta1 = "Hola, soy Carlos de la oficina. Necesito preguntarle a $TITULAR sobre el reporte de ventas.",
            respuesta2 = "No es urgente, solo dígale que me devuelva la llamada cuando se desocupe.",
        ),
        Escenario(
            id = "legitimo-familia",
            titulo = "Llamada familiar",
            descripcion = "La mamá confirma un almuerzo",
            esperado = Categoria.LEGITIMO,
            numero = "+57 310 228 9134",
            ubicacion = "Colombia",
            respuesta1 = "Hola, habla Marta, la mamá de $TITULAR. La llamo para confirmar el almuerzo del domingo.",
            respuesta2 = "No, no es urgente. Dígale que me llame cuando pueda.",
        ),
        Escenario(
            id = "legitimo-domicilio",
            titulo = "Mensajero con un paquete",
            descripcion = "Coordina una entrega en la puerta",
            esperado = Categoria.LEGITIMO,
            numero = "+57 321 660 4410",
            ubicacion = "Colombia",
            respuesta1 = "Buenas, le hablo de la empresa de mensajería. Tengo un paquete para entregar en su dirección.",
            respuesta2 = "Estoy afuera del edificio, solo necesito que alguien reciba el pedido.",
        ),
    )

    fun porId(id: String): Escenario = todos.first { it.id == id }
}
