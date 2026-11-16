package com.xavory.maxfc

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

typealias Pt = Pair<Float, Float>

@Entity
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "", val number: Int = 1, val height: Int = 180, val weight: Int = 75,
    val foot: String = "Right", val pos: String = "CM", val secondary: String = "",
    val style: String = "Box-to-Box",
    val shooting: Int = 12, val passing: Int = 12, val speed: Int = 12,
    val strength: Int = 12, val stamina: Int = 12, val defending: Int = 12
)

@Entity
data class Formation(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String = "", val data: String = "")

@Dao
interface MaxDao {
    @Query("SELECT * FROM Player ORDER BY number") fun players(): Flow<List<Player>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlayer(p: Player): Long
    @Delete suspend fun deletePlayer(p: Player)
    @Query("SELECT * FROM Formation ORDER BY id") fun formations(): Flow<List<Formation>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFormation(f: Formation): Long
    @Delete suspend fun deleteFormation(f: Formation)
    @Query("UPDATE Formation SET data = :d WHERE id = :id") suspend fun setData(id: Long, d: String)
}

@Database(entities = [Player::class, Formation::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MaxDao

    companion object {
        @Volatile private var inst: Db? = null
        fun get(c: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "maxfc.db").build().also { inst = it }
        }
    }
}

/** A drawing on the pitch. type: line | arrow | zone | free. Points are normalised 0..1. */
class Mark(val type: String, val pts: List<Pt>)

val DEFAULT_SPOTS: List<Pt> = listOf(
    .5f to .92f, .15f to .74f, .38f to .76f, .62f to .76f, .85f to .74f, .5f to .6f,
    .33f to .47f, .67f to .47f, .2f to .26f, .5f to .17f, .8f to .26f
)

/** One formation: starting XI, subs, and three independent phases (0 kick off, 1 in possession, 2 out of possession). */
class Tactic(
    val xi: MutableList<Long> = mutableListOf(),
    val subs: MutableList<Long> = mutableListOf(),
    val pos: List<MutableMap<Long, Pt>> = List(3) { mutableMapOf() },
    val draw: List<MutableList<Mark>> = List(3) { mutableListOf() }
) {
    fun at(k: Int, id: Long): Pt = pos[k][id] ?: DEFAULT_SPOTS[xi.indexOf(id).coerceIn(0, 10)]

    fun remove(id: Long) {
        xi.remove(id); subs.remove(id)
        for (k in 0..2) pos[k].remove(id)
    }

    fun toXi(id: Long, replace: Long? = null) {
        if (id in xi) return
        subs.remove(id)
        if (replace != null && replace in xi) {
            xi[xi.indexOf(replace)] = id
            for (k in 0..2) { val p = pos[k].remove(replace); if (p != null) pos[k][id] = p }
            subs.add(replace)
        } else if (xi.size < 11) {
            xi.add(id)
            val d = DEFAULT_SPOTS[xi.size - 1]
            for (k in 0..2) pos[k][id] = d
        }
    }

    fun toSubs(id: Long) { remove(id); subs.add(id) }

    fun json(): String = JSONObject().apply {
        put("xi", JSONArray(xi)); put("subs", JSONArray(subs))
        put("pos", JSONArray(pos.map { m ->
            JSONObject().also { o -> m.forEach { (k, v) -> o.put(k.toString(), JSONArray(listOf(v.first, v.second))) } }
        }))
        put("draw", JSONArray(draw.map { l ->
            JSONArray(l.map { s -> JSONObject().put("t", s.type).put("p", JSONArray(s.pts.flatMap { listOf(it.first, it.second) })) })
        }))
    }.toString()

    companion object {
        fun parse(s: String): Tactic {
            val t = Tactic()
            val o = JSONObject(s)
            val a = o.getJSONArray("xi"); for (i in 0 until a.length()) t.xi.add(a.getLong(i))
            val b = o.getJSONArray("subs"); for (i in 0 until b.length()) t.subs.add(b.getLong(i))
            val p = o.getJSONArray("pos"); val d = o.getJSONArray("draw")
            for (k in 0..2) {
                val po = p.getJSONObject(k)
                po.keys().forEach { key ->
                    val v = po.getJSONArray(key)
                    t.pos[k][key.toLong()] = Pair(v.getDouble(0).toFloat(), v.getDouble(1).toFloat())
                }
                val dl = d.getJSONArray(k)
                for (j in 0 until dl.length()) {
                    val so = dl.getJSONObject(j); val pa = so.getJSONArray("p")
                    t.draw[k].add(Mark(so.getString("t"), List(pa.length() / 2) {
                        Pair(pa.getDouble(2 * it).toFloat(), pa.getDouble(2 * it + 1).toFloat())
                    }))
                }
            }
            return t
        }
    }
}
