package com.newyear.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.math.sin
import kotlin.random.Random

class SnowView(ctx: Context) : View(ctx) {
    private class Flake(var x: Float, var y: Float, val r: Float, val v: Float, val d: Float)

    private val flakes = ArrayList<Flake>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFFFB300.toInt(), 0xFF1E88E5.toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (flakes.isEmpty() && w > 0) {
            repeat(70) {
                flakes.add(Flake(Random.nextFloat() * w, Random.nextFloat() * h,
                    2f + Random.nextFloat() * 4f, 1f + Random.nextFloat() * 2.5f, Random.nextFloat() * 6.28f))
            }
        }
        p.color = Color.WHITE
        for (f in flakes) {
            f.y += f.v
            f.x += sin((f.y / 40f + f.d).toDouble()).toFloat() * 0.8f
            if (f.y > h) { f.y = -5f; f.x = Random.nextFloat() * w }
            c.drawCircle(f.x, f.y, f.r, p)
        }
        var x = 12f
        var i = 0
        while (x < w) {
            p.color = colors[i % 4]
            c.drawCircle(x, 10f + (if (i % 2 == 0) 0f else 8f), 7f, p)
            x += 34f
            i++
        }
        postInvalidateDelayed(33)
    }
}

class MainActivity : Activity() {
    private val servers = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var nick: EditText
    private lateinit var master: EditText
    private lateinit var ip: EditText
    private lateinit var status: TextView
    private var game = "valve"

    private val gold = 0xFFFFC107.toInt()
    private val mono = Typeface.MONOSPACE

    private fun tv(text: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.typeface = mono
        return t
    }

    private fun btn(text: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.typeface = mono
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(color)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun et(hint: String, text: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(text)
        e.typeface = mono
        e.setTextColor(gold)
        e.setHintTextColor(0xFF8899AA.toInt())
        e.setSingleLine()
        return e
    }

    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (x in v) r.addView(x, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prefs = getSharedPreferences("ny", MODE_PRIVATE)

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF0B1F3A.toInt())

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(24, 70, 24, 24)

        col.addView(tv("С НОВЫМ ГОДОМ!", 26f, gold))
        col.addView(tv("Xash3D launcher - классика онлайн", 13f, 0xFF9FD8FF.toInt()))

        nick = et("Ник", prefs.getString("nick", "Player") ?: "Player")
        master = et("Мастер-сервер host:порт", prefs.getString("master", "") ?: "")
        ip = et("Быстрый вход: ip:порт", "")
        status = tv("Игра: Half-Life", 13f, Color.WHITE)

        col.addView(nick)
        col.addView(master)
        col.addView(row(
            btn("Half-Life", 0xFF2E7D32.toInt()) { game = "valve"; status.text = "Игра: Half-Life" },
            btn("CS 1.6", 0xFFC62828.toInt()) { game = "cstrike"; status.text = "Игра: Counter-Strike 1.6" }
        ))
        col.addView(btn("Обновить список серверов", 0xFF1565C0.toInt()) { refresh() })
        col.addView(status)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, servers)
        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> play(servers[pos]) }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(ip)
        col.addView(btn("Играть", 0xFFEF6C00.toInt()) { play(ip.text.toString().trim()) })

        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.addView(SnowView(this), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun refresh() {
        val parts = master.text.toString().trim().split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 27010
        if (host.isEmpty()) { status.text = "Введите адрес мастер-сервера"; return }
        getSharedPreferences("ny", MODE_PRIVATE).edit().putString("master", master.text.toString().trim()).apply()
        status.text = "Запрос..."
        Thread {
            val res = ArrayList<String>()
            var err: String? = null
            try {
                DatagramSocket().use { sock ->
                    sock.soTimeout = 3000
                    val q = byteArrayOf(0x31, 0xFF.toByte()) + "0.0.0.0:0".toByteArray() + byteArrayOf(0, 0)
                    sock.send(DatagramPacket(q, q.size, InetAddress.getByName(host), port))
                    var done = false
                    while (!done) {
                        val buf = ByteArray(2048)
                        val pk = DatagramPacket(buf, buf.size)
                        sock.receive(pk)
                        var i = 6
                        while (i + 6 <= pk.length) {
                            val a = (0 until 4).map { buf[i + it].toInt() and 0xFF }
                            val pt = ((buf[i + 4].toInt() and 0xFF) shl 8) or (buf[i + 5].toInt() and 0xFF)
                            i += 6
                            if (a.all { it == 0 } && pt == 0) { done = true; break }
                            res.add(a.joinToString(".") + ":" + pt)
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                if (res.isEmpty()) err = "Мастер не ответил"
            } catch (e: Exception) {
                err = e.message ?: e.toString()
            }
            runOnUiThread {
                servers.clear()
                servers.addAll(res)
                adapter.notifyDataSetChanged()
                status.text = err ?: "Серверов: ${res.size}"
            }
        }.start()
    }

    private fun play(addr: String) {
        val name = nick.text.toString().trim().ifEmpty { "Player" }
        getSharedPreferences("ny", MODE_PRIVATE).edit().putString("nick", name).apply()
        val args = "-game $game +name \"$name\"" + if (addr.isNotEmpty()) " +connect $addr" else ""
        val pkg = "su.xash.engine"
        try {
            val i = Intent().setComponent(ComponentName(pkg, "$pkg.XashActivity"))
            i.putExtra("argv", args)
            i.putExtra("gamedir", game)
            startActivity(i)
        } catch (e: Exception) {
            val li = packageManager.getLaunchIntentForPackage(pkg)
            if (li != null) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("xash", "connect $addr"))
                Toast.makeText(this, "Команда скопирована: вставьте в консоль", Toast.LENGTH_LONG).show()
                startActivity(li)
            } else {
                Toast.makeText(this, "Установите Xash3D FWGS", Toast.LENGTH_LONG).show()
            }
        }
    }
}
