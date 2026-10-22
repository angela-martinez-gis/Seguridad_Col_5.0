package co.seguridad.agentellamadas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.seguridad.agentellamadas.llamada.Actividad
import co.seguridad.agentellamadas.llamada.Desenlace
import co.seguridad.agentellamadas.llamada.EstadoLlamada
import co.seguridad.agentellamadas.llamada.Fase
import co.seguridad.agentellamadas.llamada.Mensaje
import co.seguridad.agentellamadas.motor.Categoria
import co.seguridad.agentellamadas.motor.Clasificacion

@Composable
fun ConversacionPantalla(
    estado: EstadoLlamada,
    alColgar: () -> Unit,
    alForzar: (Categoria?) -> Unit,
    alReportar: () -> Unit,
    alVolver: () -> Unit,
) {
    var director by remember { mutableStateOf(false) }
    val categoria = estado.veredicto?.categoria
    val colorEstado by animateColorAsState(Colores.de(categoria), label = "colorEstado")

    Box(Modifier.fillMaxSize().background(Colores.fondo).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            BarraEstado(estado, colorEstado) { director = !director }
            DatosLlamada(estado)
            Chat(estado, Modifier.weight(1f))
            LineaActividad(estado.actividad)
            estado.analisis?.let { PanelSenales(it) }
            if (estado.fase == Fase.CONVERSANDO) Controles(alColgar)
        }
        AnimatedVisibility(
            visible = estado.fase == Fase.FINALIZADA,
            enter = fadeIn() + slideInVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Resultado(estado, alReportar, alVolver)
        }
        AnimatedVisibility(visible = director, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)) {
            PanelDirector(estado.forzado, alForzar, alCerrar = { director = false })
        }
    }
}

@Composable
private fun BarraEstado(estado: EstadoLlamada, color: Color, alMantener: () -> Unit) {
    val texto = when (estado.veredicto?.categoria) {
        Categoria.FRAUDE -> "ALERTA: FRAUDE DETECTADO"
        Categoria.SPAM -> "SPAM DETECTADO: ${estado.veredicto.tipo?.etiqueta?.uppercase() ?: "SIN MOTIVO CLARO"}"
        Categoria.LEGITIMO -> "LLAMADA VERIFICADA Y PERMITIDA"
        else -> if (estado.actividad == Actividad.ANALIZANDO) "ANALIZANDO…" else "ESCUCHANDO · ANALIZANDO"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(color)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { alMantener() }) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El ámbar necesita texto oscuro para leerse en un proyector.
        val tinta = if (estado.veredicto?.categoria == Categoria.SPAM) Color(0xFF1A1300) else Color.White
        Text(texto, color = tinta, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        // Indicador discreto para el presentador cuando hay una ruta forzada.
        if (estado.forzado != null) Box(Modifier.size(8.dp).clip(CircleShape).background(tinta.copy(alpha = 0.7f)))
    }
}

@Composable
private fun DatosLlamada(estado: EstadoLlamada) {
    val e = estado.escenario ?: return
    Row(
        Modifier.fillMaxWidth().background(Colores.superficie).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PhoneInTalk, null, tint = Colores.textoSuave)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.numero, color = Colores.texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                if (estado.desenlace == Desenlace.TRANSFERIDA) "Transferida a ti" else "Atendida por el asistente",
                color = Colores.textoSuave,
                fontSize = 13.sp,
            )
        }
        Text("%02d:%02d".format(estado.segundos / 60, estado.segundos % 60), color = Colores.texto, fontSize = 16.sp)
    }
}

@Composable
private fun Chat(estado: EstadoLlamada, modifier: Modifier) {
    val lista = rememberLazyListState()
    val total = estado.mensajes.size + if (estado.subtitulo != null) 1 else 0
    LaunchedEffect(total, estado.subtitulo) {
        if (total > 0) lista.animateScrollToItem(total - 1)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = lista,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(estado.mensajes) { Burbuja(it, parcial = false) }
        estado.subtitulo?.let { sub ->
            item { Burbuja(Mensaje(false, sub.ifEmpty { "…" }), parcial = true) }
        }
    }
}

@Composable
private fun Burbuja(m: Mensaje, parcial: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.deAgente) Alignment.End else Alignment.Start) {
        Text(
            if (m.deAgente) "Asistente" else "Emisor",
            color = Colores.textoSuave,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            m.texto,
            color = Colores.texto,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontStyle = if (parcial) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (m.deAgente) 18.dp else 4.dp,
                        bottomEnd = if (m.deAgente) 4.dp else 18.dp,
                    ),
                )
                .background(if (m.deAgente) Colores.azul else Colores.burbujaEmisor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LineaActividad(actividad: Actividad) {
    if (actividad == Actividad.NINGUNA) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(actividad.texto, color = Colores.textoSuave, fontSize = 14.sp)
        if (actividad == Actividad.ANALIZANDO || actividad == Actividad.CONTESTANDO) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Colores.azul, trackColor = Colores.superficieAlta)
        }
    }
}

@Composable
private fun PanelSenales(c: Clasificacion) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Colores.superficie)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val maximo = maxOf(10, c.puntajes.values.max())
        for (cat in listOf(Categoria.FRAUDE, Categoria.SPAM, Categoria.LEGITIMO)) {
            Medidor(cat.etiqueta, c.puntajes.getValue(cat), maximo, Colores.de(cat))
        }
        c.motivos.take(3).forEach { motivo ->
            Text("• $motivo", color = Colores.texto, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Medidor(etiqueta: String, valor: Int, maximo: Int, color: Color) {
    val progreso by animateFloatAsState(valor.toFloat() / maximo, label = "medidor")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, color = Colores.textoSuave, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Colores.superficieAlta)) {
            Box(Modifier.fillMaxWidth(progreso).height(8.dp).background(color))
        }
        Text("$valor", color = Colores.texto, fontSize = 13.sp, modifier = Modifier.width(28.dp).padding(start = 8.dp))
    }
}

@Composable
private fun Controles(alColgar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlInactivo(Icons.AutoMirrored.Filled.VolumeUp, "Altavoz", activo = true)
        ControlInactivo(Icons.Filled.MicOff, "Silencio", activo = false)
        ControlInactivo(Icons.Filled.Dialpad, "Teclado", activo = false)
        BotonRedondo(Icons.Filled.CallEnd, "Colgar", Colores.rojo, alColgar)
    }
}

@Composable
private fun ControlInactivo(icono: androidx.compose.ui.graphics.vector.ImageVector, etiqueta: String, activo: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (activo) Colores.azul.copy(alpha = 0.35f) else Colores.superficieAlta),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, etiqueta, tint = Colores.texto) }
        Spacer(Modifier.height(6.dp))
        Text(etiqueta, color = Colores.textoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun Resultado(estado: EstadoLlamada, alReportar: () -> Unit, alVolver: () -> Unit) {
    val v = estado.veredicto
    val color = Colores.de(v?.categoria)
    val (titulo, accion) = when (estado.desenlace) {
        Desenlace.COLGADA_FRAUDE -> "Fraude detectado" to "El asistente colgó la llamada sin entregar información."
        Desenlace.COLGADA_SPAM -> "Spam detectado" to "El asistente declinó con cortesía y colgó."
        Desenlace.TRANSFERIDA -> "Llamada legítima" to "El asistente verificó la llamada y te la comunicó."
        Desenlace.TOMADA_POR_USUARIO -> "Contestaste tú" to "El asistente no intervino en esta llamada."
        Desenlace.RECHAZADA -> "Llamada rechazada" to "Rechazaste la llamada antes de que el asistente contestara."
        Desenlace.CORTADA -> "Llamada finalizada" to "Colgaste antes de que el asistente terminara el análisis."
        null -> "" to ""
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Colores.superficieAlta)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (v?.categoria) {
                    Categoria.FRAUDE -> Icons.Filled.GppBad
                    Categoria.SPAM -> Icons.Filled.Block
                    Categoria.LEGITIMO -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.CallEnd
                },
                null,
                tint = color,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(titulo, color = Colores.texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (v != null) {
                    Text(
                        listOfNotNull(v.tipo?.etiqueta, "confianza ${v.confianza} %").joinToString(" · "),
                        color = color,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Text(accion, color = Colores.texto, fontSize = 15.sp)
        v?.motivosDelVeredicto?.take(3)?.forEach { Text("• $it", color = Colores.textoSuave, fontSize = 14.sp) }
        if (v?.categoria == Categoria.FRAUDE) {
            Text(
                "Recuerda: ningún banco ni entidad pide claves, códigos o datos de la tarjeta por teléfono.",
                color = Colores.texto,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (v?.categoria == Categoria.FRAUDE || v?.categoria == Categoria.SPAM) {
                OutlinedButton(onClick = alReportar, enabled = !estado.reportado, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Flag, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (estado.reportado) "Número reportado" else "Reportar número")
                }
            }
            Button(
                onClick = alVolver,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Colores.azul, contentColor = Color.White),
            ) { Text("Nueva llamada") }
        }
    }
}

@Composable
private fun PanelDirector(forzado: Categoria?, alForzar: (Categoria?) -> Unit, alCerrar: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B121A))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Modo Director", color = Colores.texto, fontWeight = FontWeight.Bold)
        Text("Fuerza el resultado de la siguiente clasificación.", color = Colores.textoSuave, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (cat in listOf(Categoria.FRAUDE, Categoria.SPAM, Categoria.LEGITIMO)) {
                val elegido = forzado == cat
                Button(
                    onClick = { alForzar(if (elegido) null else cat) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (elegido) Colores.de(cat) else Colores.superficieAlta,
                        contentColor = Color.White,
                    ),
                ) { Text(cat.etiqueta, fontSize = 13.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { alForzar(null) }, modifier = Modifier.weight(1f)) { Text("Sin forzar") }
            OutlinedButton(onClick = alCerrar, modifier = Modifier.weight(1f)) { Text("Cerrar") }
        }
    }
}
