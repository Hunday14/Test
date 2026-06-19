package com.tapempire

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    // ─── Game state ───────────────────────────────────────────────────────────
    private var gold = 0.0
    private var totalGoldEarned = 0.0
    private var totalClicks = 0L

    private var clickAddBonus = 0.0
    private var clickMultiplier = 1.0
    private val goldPerClick get() = (1.0 + clickAddBonus) * clickMultiplier

    // ─── Buildings ────────────────────────────────────────────────────────────
    data class Building(
        val id: String,
        val name: String,
        val emoji: String,
        val baseCost: Double,
        val baseGps: Double,
        var count: Int = 0,
        var multiplier: Double = 1.0
    ) {
        fun currentCost() = baseCost * 1.15.pow(count.toDouble())
        fun effectiveGps() = baseGps * count * multiplier
    }

    private val buildings = mutableListOf(
        Building("pickaxe",    "Ásó",       "⛏",  15.0,              0.1),
        Building("mine",       "Bánya",     "🪨",  100.0,             0.5),
        Building("workshop",   "Műhely",    "🔨",  1_100.0,           4.0),
        Building("blacksmith", "Kovács",    "⚒",  12_000.0,          20.0),
        Building("factory",    "Gyár",      "🏭",  130_000.0,         100.0),
        Building("bank",       "Bank",      "🏦",  1_400_000.0,       400.0),
        Building("alchemist",  "Alkimista", "⚗",  20_000_000.0,      1_600.0),
        Building("portal",     "Portál",    "🌀",  330_000_000.0,     6_666.0)
    )

    // ─── Upgrades ─────────────────────────────────────────────────────────────
    data class Upgrade(
        val id: String,
        val name: String,
        val description: String,
        val cost: Double,
        var purchased: Boolean = false
    )

    private val upgrades = mutableListOf(
        Upgrade("better_fingers", "Jobb ujjak",       "+1 arany klikkenként",            100.0),
        Upgrade("double_tap",     "Dupla koppintás",  "×2 klikk erő",                   500.0),
        Upgrade("golden_touch",   "Arany érintés",    "×5 klikk erő",                   5_000.0),
        Upgrade("mega_click",     "Mega klikk",       "×10 klikk erő",                  50_000.0),
        Upgrade("ultra_click",    "Ultra klikk",      "×100 klikk erő",                 1_000_000.0),
        Upgrade("pickaxe_up",     "Éles ásó",         "Ásók 2× annyit termelnek",       500.0),
        Upgrade("mine_up",        "Mélyebb bánya",    "Bányák 2× annyit termelnek",     5_000.0),
        Upgrade("workshop_up",    "Jobb szerszámok",  "Műhelyek 2× annyit termelnek",   55_000.0),
        Upgrade("blacksmith_up",  "Mester kovács",    "Kovácsok 2× annyit termelnek",   600_000.0),
        Upgrade("factory_up",     "Automatizálás",    "Gyárak 2× annyit termelnek",     6_500_000.0)
    )

    // ─── UI refs ──────────────────────────────────────────────────────────────
    private lateinit var tvGold: TextView
    private lateinit var tvGps: TextView
    private lateinit var tvGpc: TextView
    private lateinit var tvClicks: TextView
    private lateinit var btnClick: Button
    private lateinit var buildingsLayout: LinearLayout
    private lateinit var upgradesLayout: LinearLayout

    private val decFmt = DecimalFormat("#,###.##")
    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 50L

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadGame()
        rebuildShop()
        startLoop()
    }

    override fun onPause() {
        super.onPause()
        saveGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        saveGame()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────
    private fun bindViews() {
        tvGold  = findViewById(R.id.tv_gold)
        tvGps   = findViewById(R.id.tv_gps)
        tvGpc   = findViewById(R.id.tv_gpc)
        tvClicks = findViewById(R.id.tv_clicks)
        btnClick = findViewById(R.id.btn_click)
        buildingsLayout  = findViewById(R.id.buildings_layout)
        upgradesLayout   = findViewById(R.id.upgrades_layout)
        btnClick.setOnClickListener { onTap() }
    }

    // ─── Game loop ────────────────────────────────────────────────────────────
    private fun startLoop() {
        handler.post(object : Runnable {
            override fun run() {
                val earned = gps() * tickMs / 1000.0
                gold += earned
                totalGoldEarned += earned
                updateHud()
                refreshBuyButtons()
                handler.postDelayed(this, tickMs)
            }
        })
    }

    // ─── Tap ──────────────────────────────────────────────────────────────────
    private fun onTap() {
        val earned = goldPerClick
        gold += earned
        totalGoldEarned += earned
        totalClicks++
        animateBtn()
        updateHud()
        refreshBuyButtons()
    }

    private fun animateBtn() {
        val sx = ObjectAnimator.ofFloat(btnClick, "scaleX", 1f, 0.85f, 1f)
        val sy = ObjectAnimator.ofFloat(btnClick, "scaleY", 1f, 0.85f, 1f)
        AnimatorSet().apply { playTogether(sx, sy); duration = 120; start() }
    }

    // ─── HUD ──────────────────────────────────────────────────────────────────
    private fun updateHud() {
        tvGold.text   = fmt(gold)
        tvGps.text    = "${fmt(gps())}/mp"
        tvGpc.text    = "${fmt(goldPerClick)}/klikk"
        tvClicks.text = "$totalClicks klikk"
    }

    private fun refreshBuyButtons() {
        buildings.forEach { b ->
            buildingsLayout.findViewWithTag<Button>("btn_b_${b.id}")?.isEnabled = gold >= b.currentCost()
        }
        upgrades.filter { !it.purchased }.forEach { u ->
            upgradesLayout.findViewWithTag<Button>("btn_u_${u.id}")?.isEnabled = gold >= u.cost
        }
    }

    // ─── Shop ─────────────────────────────────────────────────────────────────
    private fun rebuildShop() {
        buildingsLayout.removeAllViews()
        upgradesLayout.removeAllViews()
        buildings.forEach { buildingsLayout.addView(buildingRow(it)) }
        upgrades.filter { !it.purchased }.forEach { upgradesLayout.addView(upgradeRow(it)) }
    }

    private fun buildingRow(b: Building): View {
        val row = row(R.drawable.shop_item_bg)
        val info = infoCol()
        info.addView(label("${b.emoji}  ${b.name}  ×${b.count}", 15f, "#FFD700", bold = true))
        info.addView(label("Ár: ${fmt(b.currentCost())} arany", 12f, "#cccccc"))
        info.addView(label("+${fmt(b.baseGps)}/mp  •  összes: ${fmt(b.effectiveGps())}/mp", 11f, "#777777"))
        row.addView(info)
        val btn = buyBtn("btn_b_${b.id}", "Vesz", 80) { buyBuilding(b) }
        btn.isEnabled = gold >= b.currentCost()
        row.addView(btn)
        return row
    }

    private fun upgradeRow(u: Upgrade): View {
        val row = row(R.drawable.upgrade_item_bg)
        val info = infoCol()
        info.addView(label("✨  ${u.name}", 15f, "#e94560", bold = true))
        info.addView(label(u.description, 12f, "#cccccc"))
        info.addView(label("Ár: ${fmt(u.cost)} arany", 11f, "#777777"))
        row.addView(info)
        val btn = buyBtn("btn_u_${u.id}", "Vesz", 80) { buyUpgrade(u) }
        btn.isEnabled = gold >= u.cost
        row.addView(btn)
        return row
    }

    // ─── Purchases ────────────────────────────────────────────────────────────
    private fun buyBuilding(b: Building) {
        val cost = b.currentCost()
        if (gold < cost) { toast("Nincs elég arany!"); return }
        gold -= cost
        b.count++
        rebuildShop()
        updateHud()
    }

    private fun buyUpgrade(u: Upgrade) {
        if (gold < u.cost) { toast("Nincs elég arany!"); return }
        gold -= u.cost
        u.purchased = true
        applyUpgrade(u)
        rebuildShop()
        updateHud()
        toast("${u.name} aktiválva!")
    }

    private fun applyUpgrade(u: Upgrade) {
        when (u.id) {
            "better_fingers" -> clickAddBonus   += 1.0
            "double_tap"     -> clickMultiplier *= 2.0
            "golden_touch"   -> clickMultiplier *= 5.0
            "mega_click"     -> clickMultiplier *= 10.0
            "ultra_click"    -> clickMultiplier *= 100.0
            "pickaxe_up"     -> buildings.find { it.id == "pickaxe"    }?.let { it.multiplier *= 2.0 }
            "mine_up"        -> buildings.find { it.id == "mine"       }?.let { it.multiplier *= 2.0 }
            "workshop_up"    -> buildings.find { it.id == "workshop"   }?.let { it.multiplier *= 2.0 }
            "blacksmith_up"  -> buildings.find { it.id == "blacksmith" }?.let { it.multiplier *= 2.0 }
            "factory_up"     -> buildings.find { it.id == "factory"    }?.let { it.multiplier *= 2.0 }
        }
    }

    // ─── Stats ────────────────────────────────────────────────────────────────
    private fun gps() = buildings.sumOf { it.effectiveGps() }

    // ─── Save / Load ──────────────────────────────────────────────────────────
    private fun prefs() = getSharedPreferences("tap_empire_v1", Context.MODE_PRIVATE)

    private fun saveGame() {
        val p = prefs().edit()
        p.putLong("gold_bits",       gold.toBits())
        p.putLong("total_gold_bits", totalGoldEarned.toBits())
        p.putLong("total_clicks",    totalClicks)
        p.putLong("saved_at",        System.currentTimeMillis())
        buildings.forEach { p.putInt("b_${it.id}", it.count) }
        upgrades.forEach  { p.putBoolean("u_${it.id}", it.purchased) }
        p.apply()
    }

    private fun loadGame() {
        val p = prefs()
        gold            = Double.fromBits(p.getLong("gold_bits",       0.0.toBits()))
        totalGoldEarned = Double.fromBits(p.getLong("total_gold_bits", 0.0.toBits()))
        totalClicks     = p.getLong("total_clicks", 0L)

        buildings.forEach { it.count = p.getInt("b_${it.id}", 0); it.multiplier = 1.0 }

        clickAddBonus   = 0.0
        clickMultiplier = 1.0
        upgrades.forEach { u ->
            u.purchased = p.getBoolean("u_${u.id}", false)
            if (u.purchased) applyUpgrade(u)
        }

        val savedAt = p.getLong("saved_at", 0L)
        if (savedAt > 0L) {
            val offlineSec = ((System.currentTimeMillis() - savedAt) / 1000.0).coerceAtMost(3600.0)
            val offlineGold = gps() * offlineSec * 0.5
            if (offlineGold >= 1.0) {
                gold += offlineGold
                totalGoldEarned += offlineGold
                toast("Offline kereset: +${fmt(offlineGold)} arany!")
            }
        }
    }

    // ─── View helpers ─────────────────────────────────────────────────────────
    private fun row(bgRes: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(bgRes)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(6) }
    }

    private fun infoCol() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun label(text: String, size: Float, hex: String, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(hex))
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun buyBtn(tag: String, text: String, widthDp: Int, onClick: () -> Unit) =
        Button(this).apply {
            this.tag = tag
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#111111"))
            setBackgroundResource(R.drawable.btn_buy_bg)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(40))
            setOnClickListener { onClick() }
        }

    // ─── Utils ────────────────────────────────────────────────────────────────
    private fun fmt(n: Double): String {
        if (n < 0) return "0"
        return when {
            n >= 1e15 -> "${decFmt.format(n / 1e15)}Qa"
            n >= 1e12 -> "${decFmt.format(n / 1e12)}T"
            n >= 1e9  -> "${decFmt.format(n / 1e9)}G"
            n >= 1e6  -> "${decFmt.format(n / 1e6)}M"
            n >= 1e3  -> "${decFmt.format(n / 1e3)}K"
            else      -> decFmt.format(n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
