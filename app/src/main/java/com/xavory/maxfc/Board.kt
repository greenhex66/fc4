package com.xavory.maxfc

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*

fun roleColor(pos: String): Color = when (pos) {
    "GK" -> Color(0xFF7EC8FF)
    "CB", "LB", "RB" -> Color(0xFFFFC83D)
    "DM", "CM", "AM" -> Color(0xFF3DDC84)
    else -> Color(0xFFFF4D4D)
}

fun markColor(t: String): Color = when (t) {
    "line" -> Color.White
    "arrow" -> Color(0xFFFFD54F)
    "zone" -> Color(0xFF4DD0E1)
    else -> Color(0xFFFF9800)
}

fun DrawScope.pitch() {
    drawRect(Color(0xFF0B3D2E))
    val w = size.width; val h = size.height; val m = w * 0.04f
    val line = Color.White.copy(alpha = 0.35f); val st = Stroke(width = 3f)
    val iw = w - 2 * m; val ih = h - 2 * m
    drawRect(line, Offset(m, m), Size(iw, ih), style = st)
    drawLine(line, Offset(m, h / 2), Offset(w - m, h / 2), 3f)
    drawCircle(line, w * 0.14f, Offset(w / 2, h / 2), style = st)
    val pw = iw * 0.593f; val ph = ih * 0.157f; val gw = iw * 0.27f; val gh = ih * 0.052f; val gl = iw * 0.108f
    drawRect(line, Offset((w - pw) / 2, m), Size(pw, ph), style = st)
    drawRect(line, Offset((w - pw) / 2, h - m - ph), Size(pw, ph), style = st)
    drawRect(line, Offset((w - gw) / 2, m), Size(gw, gh), style = st)
    drawRect(line, Offset((w - gw) / 2, h - m - gh), Size(gw, gh), style = st)
    drawRect(line, Offset((w - gl) / 2, m * 0.4f), Size(gl, m * 0.6f), style = st)
    drawRect(line, Offset((w - gl) / 2, h - m), Size(gl, m * 0.6f), style = st)
}

fun DrawScope.mark(mk: Mark) {
    val w = size.width; val h = size.height; val col = markColor(mk.type)
    val p = mk.pts.map { Offset(it.first * w, it.second * h) }
    if (p.size < 2) return
    val s = p.first(); val e = p.last()
    when (mk.type) {
        "line", "arrow" -> {
            drawLine(col, s, e, 5f)
            if (mk.type == "arrow") {
                val a = atan2(e.y - s.y, e.x - s.x)
                for (d in listOf(2.6f, -2.6f)) drawLine(col, e, Offset(e.x + 30f * cos(a + d), e.y + 30f * sin(a + d)), 5f)
            }
        }
        "zone" -> {
            val tl = Offset(min(s.x, e.x), min(s.y, e.y)); val sz = Size(abs(e.x - s.x), abs(e.y - s.y))
            drawOval(col.copy(alpha = 0.25f), tl, sz)
            drawOval(col, tl, sz, style = Stroke(3f))
        }
        else -> {
            val path = Path().apply { moveTo(s.x, s.y); p.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, col, style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}

fun hitTest(t: Tactic, ps: Map<Long, Player>, phase: Int, o: Offset, w: Float, h: Float): Long? =
    t.xi.lastOrNull { id ->
        val p = t.at(phase, id)
        ps.containsKey(id) && (Offset(p.first * w, p.second * h) - o).getDistance() < w * 0.07f
    }

@Composable
fun Board(
    t: Tactic, ps: Map<Long, Player>, phase: Int, tool: String, rev: Int,
    onChange: () -> Unit, onTap: (Player) -> Unit, modifier: Modifier = Modifier
) {
    var cur by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var drag by remember { mutableStateOf<Long?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val change by rememberUpdatedState(onChange)
    val tap by rememberUpdatedState(onTap)
    val num = remember { Paint().apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD } }
    val nm = remember { Paint().apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * 0.66f)
        Canvas(
            Modifier.size(w, w / 0.66f)
                .pointerInput(tool, phase, ps, t) {
                    detectTapGestures { o ->
                        val fw = size.width.toFloat(); val fh = size.height.toFloat()
                        if (tool == "move") {
                            hitTest(t, ps, phase, o, fw, fh)?.let { id -> ps[id]?.let { tap(it) } }
                        } else if (tool == "erase") {
                            fun dist(m: Mark): Float {
                                val a = m.pts.first(); val b = m.pts.last()
                                val pts = m.pts + Pair((a.first + b.first) / 2, (a.second + b.second) / 2)
                                return pts.minOf { hypot(it.first * fw - o.x, it.second * fh - o.y) }
                            }
                            val best = t.draw[phase].minByOrNull { dist(it) }
                            if (best != null && dist(best) < fw * 0.12f) { t.draw[phase].remove(best); tick++; change() }
                        }
                    }
                }
                .pointerInput(tool, phase, ps, t) {
                    val finish = {
                        if (drag != null) { drag = null; change() }
                        else if (cur.size > 1) { t.draw[phase].add(Mark(tool, cur)); change() }
                        cur = emptyList()
                    }
                    detectDragGestures(
                        onDragStart = { o ->
                            val fw = size.width.toFloat(); val fh = size.height.toFloat()
                            if (tool == "move") drag = hitTest(t, ps, phase, o, fw, fh)
                            else if (tool != "erase") cur = listOf(Pair(o.x / fw, o.y / fh))
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            val p = Pair((ch.position.x / size.width).coerceIn(0f, 1f), (ch.position.y / size.height).coerceIn(0f, 1f))
                            val d = drag
                            if (d != null) { t.pos[phase][d] = p; tick++ }
                            else if (cur.isNotEmpty()) cur = if (tool == "free") cur + p else listOf(cur[0], p)
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() }
                    )
                }
        ) {
            val redraw = tick + rev
            val cw = size.width; val ch = size.height
            pitch()
            t.draw[phase].forEach { mark(it) }
            if (cur.size > 1) mark(Mark(tool, cur))
            t.xi.forEach { id ->
                val p = ps[id] ?: return@forEach
                val (x, y) = t.at(phase, id)
                val c = Offset(x * cw, y * ch); val r = cw * 0.05f
                drawCircle(roleColor(p.pos), r, c, style = Stroke(5f))
                drawIntoCanvas { cv ->
                    num.textSize = r * 0.95f; nm.textSize = r * 0.6f
                    cv.nativeCanvas.drawText(p.number.toString(), c.x, c.y + r * 0.33f, num)
                    cv.nativeCanvas.drawText(p.name, c.x, c.y + r * 1.75f, nm)
                }
            }
        }
    }
}
