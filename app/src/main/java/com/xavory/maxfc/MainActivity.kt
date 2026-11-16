package com.xavory.maxfc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val Midnight = Color(0xFF0B1530)
val Navy = Color(0xFF111F42)
val RedWarn = Color(0xFFFF6B6B)

val STYLES = listOf(
    "Goal Poacher", "Fox in the Box", "Dummy Runner", "Target Man", "Deep-Lying Forward", "Creative Playmaker",
    "Prolific Winger", "Roaming Flank", "Cross Specialist", "Classic No. 10", "Hole Player", "Box-to-Box",
    "Orchestrator", "Anchor Man", "Destroyer", "Build Up", "Extra Frontman", "Offensive Fullback",
    "Fullback Finisher", "Defensive Fullback", "Offensive Goalkeeper", "Defensive Goalkeeper"
)
val POSITIONS = listOf("GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "SS", "CF")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = Db.get(this).dao()
        setContent { App(dao) }
    }
}

@Composable
fun App(dao: MaxDao) {
    val scope = rememberCoroutineScope()
    val players by dao.players().collectAsState(emptyList())
    val forms: List<Formation>? by dao.formations().collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(0) }
    var curId by rememberSaveable { mutableStateOf(-1L) }
    var rev by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(forms) {
        val f = forms ?: return@LaunchedEffect
        if (f.isEmpty()) {
            dao.saveFormation(Formation(name = "Formation 1", data = Tactic().json()))
        } else if (f.none { it.id == curId }) {
            curId = f.first().id
        }
    }
    val cur = forms?.firstOrNull { it.id == curId }
    val tactic = remember(curId, cur == null) { cur?.let { Tactic.parse(it.data) } }
    val map = players.associateBy { it.id }

    fun persist() {
        rev++
        tactic?.let { t -> scope.launch { dao.setData(curId, t.json()) } }
    }

    fun deletePlayer(p: Player) {
        tactic?.remove(p.id)
        scope.launch {
            forms.orEmpty().filter { it.id != curId }.forEach { f ->
                val t = Tactic.parse(f.data); t.remove(p.id); dao.setData(f.id, t.json())
            }
            persist()
            dao.deletePlayer(p)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF5B8CFF), background = Midnight, surface = Navy, onSurface = Color.White)) {
        BackHandler(tab != 0) { tab = 0 }
        Scaffold(
            containerColor = Midnight,
            bottomBar = {
                NavigationBar(containerColor = Navy) {
                    val icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Menu, Icons.Default.Settings)
                    listOf("Board", "Squad", "Formations", "Settings").forEachIndexed { i, s ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], s) }, label = { Text(s, fontSize = 11.sp) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> BoardTab(tactic, map, rev, cur?.name ?: "", { persist() }, { editing = it })
                    1 -> SquadTab(players, tactic, rev, { persist() }, { p -> editing = p; creating = p == null })
                    2 -> FormTab(
                        forms.orEmpty(), curId,
                        onPick = { curId = it.id; tab = 0 },
                        onNew = { scope.launch { dao.saveFormation(Formation(name = "Formation ${forms.orEmpty().size + 1}", data = Tactic().json())) } },
                        onRename = { f, n -> scope.launch { dao.saveFormation(f.copy(name = n)) } },
                        onDup = { f -> scope.launch { dao.saveFormation(Formation(name = f.name + " copy", data = f.data)) } },
                        onDel = { f -> scope.launch { dao.deleteFormation(f) } }
                    )
                    else -> SettingsTab()
                }
                if (creating || editing != null) {
                    val init = editing ?: Player(number = (players.maxOfOrNull { it.number } ?: 0) + 1)
                    PlayerDialog(
                        init,
                        onSave = { p -> scope.launch { dao.savePlayer(p) }; editing = null; creating = false },
                        onDelete = if (editing != null) ({ deletePlayer(init); editing = null; creating = false }) else null,
                        onClose = { editing = null; creating = false }
                    )
                }
            }
        }
    }
}

@Composable
fun BoardTab(t: Tactic?, ps: Map<Long, Player>, rev: Int, name: String, persist: () -> Unit, edit: (Player) -> Unit) {
    var phase by rememberSaveable { mutableStateOf(0) }
    var tool by rememberSaveable { mutableStateOf("move") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Kick Off", "In Possession", "Out of Possession").forEachIndexed { i, s ->
                FilterChip(phase == i, { phase = i }, { Text(s, fontSize = 10.sp, maxLines = 1, softWrap = false) }, Modifier.weight(1f))
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.xavory_logo), "Xavory", Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("MAX FC", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (t == null || t.xi.isEmpty()) "$name: add players to the Starting XI in Squad" else name,
                fontSize = 12.sp, color = Color.LightGray, maxLines = 1
            )
        }
        if (t != null) Board(t, ps, phase, tool, rev, persist, edit, Modifier.weight(1f).fillMaxWidth())
        else Spacer(Modifier.weight(1f))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("move" to "Move", "line" to "Pass", "arrow" to "Arrow", "zone" to "Zone", "free" to "Draw", "erase" to "Erase").forEach { (id, l) ->
                FilterChip(tool == id, { tool = id }, { Text(l) })
            }
            AssistChip({ t?.let { it.draw[phase].clear(); persist() } }, { Text("Clear all") })
        }
    }
}

@Composable
fun SquadTab(ps: List<Player>, t: Tactic?, rev: Int, persist: () -> Unit, onEdit: (Player?) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Number") }
    var swap by remember { mutableStateOf<Player?>(null) }
    val list = ps.filter { "${it.name} ${it.pos} ${it.secondary} ${it.style}".contains(q, true) }.let { l ->
        when (sort) {
            "Name" -> l.sortedBy { it.name }
            "Position" -> l.sortedBy { POSITIONS.indexOf(it.pos) }
            "Style" -> l.sortedBy { it.style }
            "Shooting" -> l.sortedByDescending { it.shooting }
            "Passing" -> l.sortedByDescending { it.passing }
            "Speed" -> l.sortedByDescending { it.speed }
            "Strength" -> l.sortedByDescending { it.strength }
            "Stamina" -> l.sortedByDescending { it.stamina }
            "Defending" -> l.sortedByDescending { it.defending }
            else -> l.sortedBy { it.number }
        }
    }
    val xi = list.filter { t != null && it.id in t.xi }
    val sb = list.filter { t != null && it.id in t.subs }
    val rs = list.filter { p -> xi.none { it.id == p.id } && sb.none { it.id == p.id } }

    Box(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search name, position, style") }, singleLine = true)
            Pick("Sort", sort, listOf("Number", "Name", "Position", "Style", "Shooting", "Passing", "Speed", "Strength", "Stamina", "Defending")) { sort = it }
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                listOf("Starting XI" to xi, "Substitutes" to sb, "Reserves" to rs).forEachIndexed { st, (title, l) ->
                    item { Text("$title (${l.size}${if (st == 0) "/11" else ""})", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold) }
                    items(l, key = { it.id }) { p ->
                        PlayerCard(
                            p, st,
                            onXi = { if (t != null) { if (t.xi.size >= 11 && p.id !in t.xi) swap = p else { t.toXi(p.id); persist() } } },
                            onSub = { if (t != null) { t.toSubs(p.id); persist() } },
                            onRes = { if (t != null) { t.remove(p.id); persist() } },
                            onEdit = { onEdit(p) }
                        )
                    }
                }
            }
        }
        FloatingActionButton({ onEdit(null) }, Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Icon(Icons.Default.Add, "Add player") }
    }
    swap?.let { s ->
        AlertDialog(
            onDismissRequest = { swap = null }, confirmButton = {},
            title = { Text("Replace who with ${s.name}?") },
            text = {
                Column {
                    if (t != null) t.xi.forEach { id ->
                        ps.firstOrNull { it.id == id }?.let { x ->
                            TextButton({ t.toXi(s.id, id); persist(); swap = null }) { Text("#${x.number} ${x.name}") }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun PlayerCard(p: Player, st: Int, onXi: () -> Unit, onSub: () -> Unit, onRes: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onEdit() }, colors = CardDefaults.cardColors(containerColor = Navy)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).border(2.dp, roleColor(p.pos), CircleShape), contentAlignment = Alignment.Center) { Text("${p.number}", fontSize = 13.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    Text("${p.pos} · ${p.style} · ${p.foot} · ${p.height}cm ${p.weight}kg", fontSize = 12.sp, color = Color.LightGray)
                }
            }
            Text("SHO ${p.shooting}  PAS ${p.passing}  SPD ${p.speed}  STR ${p.strength}  STA ${p.stamina}  DEF ${p.defending}", Modifier.padding(top = 6.dp), fontSize = 12.sp)
            Row {
                listOf("XI" to onXi, "Sub" to onSub, "Res" to onRes).forEachIndexed { i, (l, f) ->
                    FilterChip(st == i, f, { Text(l) }, Modifier.padding(end = 6.dp))
                }
            }
        }
    }
}

@Composable
fun Pick(label: String, v: String, opts: List<String>, on: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }) { Text("$label: $v") }
        DropdownMenu(open, { open = false }) {
            opts.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { on(o); open = false }) }
        }
    }
}

@Composable
fun Num(label: String, v: Int, m: Modifier, on: (Int) -> Unit) {
    OutlinedTextField(
        v.toString(), { on(it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) }, m,
        label = { Text(label, fontSize = 10.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun Attr(label: String, v: Int, on: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label $v", Modifier.width(110.dp), fontSize = 13.sp)
        Slider(v.toFloat(), { on(it.roundToInt()) }, valueRange = 1f..24f, steps = 22)
    }
}

@Composable
fun PlayerDialog(init: Player, onSave: (Player) -> Unit, onDelete: (() -> Unit)?, onClose: () -> Unit) {
    var d by remember(init.id) { mutableStateOf(init) }
    var ask by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton({ if (d.name.isNotBlank()) onSave(d) }) { Text("Save") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } },
        title = { Text(if (init.id == 0L) "New player" else "Player profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Num("No.", d.number, Modifier.weight(1f)) { d = d.copy(number = it) }
                    Num("Height cm", d.height, Modifier.weight(1f)) { d = d.copy(height = it) }
                    Num("Weight kg", d.weight, Modifier.weight(1f)) { d = d.copy(weight = it) }
                }
                Pick("Foot", d.foot, listOf("Right", "Left", "Both")) { d = d.copy(foot = it) }
                Pick("Position", d.pos, POSITIONS) { d = d.copy(pos = it) }
                OutlinedTextField(d.secondary, { d = d.copy(secondary = it) }, label = { Text("Secondary positions") }, singleLine = true)
                Pick("Style", d.style, STYLES) { d = d.copy(style = it) }
                Attr("Shooting", d.shooting) { d = d.copy(shooting = it) }
                Attr("Passing", d.passing) { d = d.copy(passing = it) }
                Attr("Speed", d.speed) { d = d.copy(speed = it) }
                Attr("Strength", d.strength) { d = d.copy(strength = it) }
                Attr("Stamina", d.stamina) { d = d.copy(stamina = it) }
                Attr("Defending", d.defending) { d = d.copy(defending = it) }
                if (onDelete != null) TextButton({ ask = true }) { Text("Delete player", color = RedWarn) }
            }
        }
    )
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        confirmButton = { TextButton({ onDelete?.invoke() }) { Text("Delete", color = RedWarn) } },
        dismissButton = { TextButton({ ask = false }) { Text("Cancel") } },
        title = { Text("Delete ${d.name}?") },
        text = { Text("The player is removed from the squad and from every formation.") }
    )
}

@Composable
fun FormTab(
    fs: List<Formation>, curId: Long, onPick: (Formation) -> Unit, onNew: () -> Unit,
    onRename: (Formation, String) -> Unit, onDup: (Formation) -> Unit, onDel: (Formation) -> Unit
) {
    var ren by remember { mutableStateOf<Formation?>(null) }
    var name by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<Formation?>(null) }
    Column(Modifier.padding(10.dp)) {
        Button(onNew) { Text("New formation") }
        LazyColumn {
            items(fs, key = { it.id }) { f ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPick(f) },
                    colors = CardDefaults.cardColors(containerColor = if (f.id == curId) Color(0xFF1E3A78) else Navy)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(f.name + if (f.id == curId) "  (open)" else "", fontWeight = FontWeight.Bold)
                        Row {
                            TextButton({ ren = f; name = f.name }) { Text("Rename") }
                            TextButton({ onDup(f) }) { Text("Duplicate") }
                            TextButton({ del = f }) { Text("Delete", color = RedWarn) }
                        }
                    }
                }
            }
        }
    }
    ren?.let { f ->
        AlertDialog(
            onDismissRequest = { ren = null },
            confirmButton = { TextButton({ if (name.isNotBlank()) onRename(f, name.trim()); ren = null }) { Text("Save") } },
            title = { Text("Rename formation") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) }
        )
    }
    del?.let { f ->
        AlertDialog(
            onDismissRequest = { del = null },
            confirmButton = { TextButton({ onDel(f); del = null }) { Text("Delete", color = RedWarn) } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } },
            title = { Text("Delete ${f.name}?") },
            text = { Text("All three phases and drawings of this formation will be lost.") }
        )
    }
}

@Composable
fun SettingsTab() {
    var s by remember { mutableStateOf("") }
    val r = s.toIntOrNull()?.let { (((it.coerceIn(40, 100) - 40) / 60f) * 23f + 1f).roundToInt() }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.xavory_logo), "Xavory", Modifier.size(96.dp))
        Text("MAX FC · MAXIS · Xavory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("PES to MAXIS rating converter (PES 40-100 maps to 1-24)")
        OutlinedTextField(
            s, { s = it.filter(Char::isDigit).take(3) }, label = { Text("PES value") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (r != null) Text("MAXIS: $r", fontSize = 24.sp)
        Text("Everything is stored on this phone. No account, no internet.", color = Color.Gray)
    }
}
