package com.dopewars.ru

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

/** Offline adaptation of the original dopewars 1.6.2 market generation rules. */
class MainActivity : Activity() {
    private data class Good(val title: String, val low: Int, val high: Int, val cheap: Boolean = false, val expensive: Boolean = false)
    private val goods = listOf(
        Good("ЛСД",1000,4400, true, false),
        Good("Кокаин",15000,29000, false, true),
        Good("Гашиш",480,1280, true, false),
        Good("Героин",5500,13000, false, true),
        Good("Седативные",11,60, true, false),
        Good("МДА",1500,4400),
        Good("Опиум",540,1250, false, true),
        Good("Фенциклидин",1000,2500),
        Good("Пейот",220,700),
        Good("Грибы",630,1300),
        Good("Стимуляторы",90,250, false, true),
        Good("Марихуана",315,890, true, false)
    )
    private val places = listOf("Салтовка", "ХТЗ", "Парк Горького", "Сумская", "Научная", "Павлово Поле", "Одесская", "Новые Дома")
    private val rng = Random.Default
    private val minMarket = intArrayOf(7,8,6,4,4,4,6,6)
    private val maxMarket = intArrayOf(12,12,12,10,12,11,12,12)
    private val special = IntArray(goods.size) // -1 cheap, +1 expensive
    private var cash = 2000L
    private var debt = 5500L
    private var bank = 0L
    private var day = 1
    private var place = 0
    private val stock = IntArray(goods.size)
    private val prices = IntArray(goods.size)
    private val market = BooleanArray(goods.size)
    private var message = "Добро пожаловать в Харьков!"
    private var ended = false
    private var capacity = 100
    private var bestScore = Long.MIN_VALUE
    private var gamesFinished = 0
    private var lastTripEvent = ""
    private lateinit var root: LinearLayout
    private val bg = Color.rgb(14, 19, 31)
    private val panel = Color.rgb(27, 35, 51)
    private val fg = Color.rgb(238, 242, 249)
    private val accent = Color.rgb(77, 194, 171)
    private fun money(n: Long) = "$" + "%,d".format(java.util.Locale.US, n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val saved = getPreferences(MODE_PRIVATE).getString("save", null)
        if (saved != null) {
            try { load(JSONObject(saved)) } catch (_: Exception) { reset() }
        } else reset()
        render()
    }
    private fun reset() {
        cash = 2000; debt = 5500; bank = 0; day = 1; place = 0; ended = false; capacity = 100; lastTripEvent = ""
        stock.fill(0); refreshPrices(); message = "Твоя история начинается на Салтовке."
        save()
    }
    private fun refreshPrices() {
        // Ported from GenerateDrugsHere(), src/serverside.c, dopewars 1.6.2.
        // C brandom() and prandom() use an EXCLUSIVE upper boundary.
        prices.fill(0); market.fill(false); special.fill(0)
        var events = if (rng.nextInt(100) < 70) 1 else 0
        if (events == 1 && rng.nextInt(100) < 40) events = 2
        if (events == 2 && rng.nextInt(100) < 5) events = 3
        var selected = 0
        while (events > 0) {
            val i = rng.nextInt(goods.size)
            if (special[i] != 0) continue
            val g = goods[i]
            if (g.expensive && (!g.cheap || rng.nextInt(100) < 50)) {
                special[i] = 1
                prices[i] = rng.nextInt(g.low, g.high) * 4
            } else if (g.cheap) {
                special[i] = -1
                prices[i] = rng.nextInt(g.low, g.high) / 4
            } else continue
            selected++; events--
        }
        val target = rng.nextInt(minMarket[place], maxMarket[place])
        var remaining = (target - selected).coerceAtLeast(0)
        while (remaining > 0) {
            val i = rng.nextInt(goods.size)
            if (prices[i] == 0) {
                prices[i] = rng.nextInt(goods[i].low, goods[i].high)
                remaining--
            }
        }
        for (i in goods.indices) market[i] = prices[i] > 0
    }
    private fun save() {
        val o = JSONObject().put("cash", cash).put("debt", debt).put("bank", bank)
            .put("day", day).put("place", place).put("ended", ended).put("message", message)
            .put("capacity", capacity).put("bestScore", bestScore).put("gamesFinished", gamesFinished).put("lastTripEvent", lastTripEvent)
            .put("stock", stock.joinToString(",")).put("prices", prices.joinToString(","))
            .put("market", market.map { if (it) "1" else "0" }.joinToString(","))
            .put("special", special.joinToString(","))
        getPreferences(MODE_PRIVATE).edit().putString("save", o.toString()).apply()
    }
    private fun load(o: JSONObject) {
        cash=o.getLong("cash"); debt=o.getLong("debt"); bank=o.getLong("bank")
        day=o.getInt("day");place=o.getInt("place");ended=o.optBoolean("ended")
        message=o.optString("message")
        // Existing saves may still contain legacy New York names in the message.
        mapOf("Бронкс" to "Салтовка", "Гетто" to "ХТЗ", "Центральный парк" to "Парк Горького",
            "Манхэттен" to "Сумская", "Кони-Айленд" to "Научная", "Бруклин" to "Павлово Поле",
            "Куинс" to "Одесская", "Статен-Айленд" to "Новые Дома", "Нью-Йорк" to "Харьков")
            .forEach { (old, new) -> message = message.replace(old, new) }
        capacity=o.optInt("capacity",100).coerceAtLeast(100)
        bestScore=o.optLong("bestScore",Long.MIN_VALUE)
        gamesFinished=o.optInt("gamesFinished",0)
        lastTripEvent=o.optString("lastTripEvent", "")
        val a=o.getString("stock").split(","); val p=o.getString("prices").split(",")
        val m=o.getString("market").split(",")
        require(a.size==goods.size && p.size==goods.size && m.size==goods.size)
        val ev=o.optString("special", "").split(",")
        for (i in goods.indices) {stock[i]=a[i].toInt();prices[i]=p[i].toInt();market[i]=m[i]=="1"; special[i]=ev.getOrNull(i)?.toIntOrNull() ?: 0}
    }
    private var activeTab = 0
    private val cyan = Color.rgb(18, 207, 185)
    private val muted = Color.rgb(155, 174, 193)
    private val blue = Color.rgb(13, 129, 200)
    private val orange = Color.rgb(249, 154, 57)

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(txt: String, size: Float = 16f, bold: Boolean = false, color: Int = fg): TextView = TextView(this).apply {
        text = txt; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(txt: String, run: () -> Unit): Button = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setAllCaps(false); textSize = 14f
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(28, 74, 96))
        setOnClickListener { run() }
    }
    private fun action(txt: String, color: Int = cyan, run: () -> Unit): TextView = label(txt, 15f, true, Color.WHITE).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = rounded(color, 11)
        setOnClickListener { run() }
    }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun paddedCard(parent: LinearLayout): LinearLayout = column().apply {
        setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(panel)
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(7)
        parent.addView(this, params)
    }
    private fun put(parent: LinearLayout, view: View, bottom: Int = 0) {
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(bottom)
        parent.addView(view, params)
    }
    private fun header(parent: LinearLayout, title: String, subtitle: String = "") {
        put(parent, label(title, 20f, true), 2)
        if (subtitle.isNotBlank()) put(parent, label(subtitle, 12f, false, muted), 8)
    }
    private fun openTab(tab: Int) { activeTab = tab; render() }
    private fun stat(parent: LinearLayout, title: String, value: String, color: Int = fg) {
        val c = column().apply { setPadding(dp(12), dp(11), dp(8), dp(11)); background = rounded(Color.rgb(18, 39, 57), 10) }
        put(c, label(title, 12f, false, muted), 5)
        put(c, label(value, 17f, true, color))
        parent.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
    }
    private fun render() {
        val shell = column().apply { setBackgroundColor(bg) }
        setContentView(shell)
        val content = column().apply { setPadding(dp(11), dp(9), dp(11), dp(7)) }
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(content) }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        put(content, label("DOPEWARS  •  ХАРЬКОВ", 19f, true, cyan), 5)
        put(content, label("День $day / 30    •    ${places[place]}", 14f, true), 8)
        when (activeTab) {
            0 -> showHome(content)
            1 -> showMap(content)
            2 -> showMarket(content)
            3 -> showInventory(content)
            4 -> showFinance(content)
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(4), dp(3), dp(5))
            setBackgroundColor(Color.rgb(16, 31, 47))
        }
        val tabs = listOf("⌂\nГлавная", "◈\nРайоны", "▤\nРынок", "▣\nБагаж", "▥\nФинансы")
        tabs.forEachIndexed { i, name ->
            val item = label(name, 12f, i == activeTab, if (i == activeTab) cyan else muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(1), dp(5), dp(1), dp(5))
                if (i == activeTab) background = rounded(Color.rgb(28, 63, 76), 8)
                setOnClickListener { openTab(i) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        shell.addView(nav)
    }
    private fun showHome(parent: LinearLayout) {
        val summary = paddedCard(parent)
        put(summary, label("Твой баланс", 14f, false, muted), 6)
        put(summary, label(money(cash), 31f, true), 13)
        val stats = LinearLayout(this)
        stat(stats, "Долг", money(debt), orange)
        stat(stats, "В банке", money(bank), cyan)
        put(summary, stats, 13)
        put(summary, label("Багаж: ${stock.sum()} / $capacity", 13f, false, muted), 5)
        put(summary, ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = capacity; progress = stock.sum(); progressTintList = android.content.res.ColorStateList.valueOf(cyan)
        }, 9)
        val news = paddedCard(parent)
        put(news, label("События", 17f, true), 6)
        put(news, label(message, 14f), 4)
        if (lastTripEvent.isNotBlank()) put(news, label(lastTripEvent, 13f, false, muted))
        if (ended) {
            val end = paddedCard(parent)
            put(end, label("Игра завершена", 21f, true), 8)
            put(end, label("Результат: ${money(score())}", 19f, true, cyan), 8)
            put(end, label("Рекорд: ${money(bestScore)}", 14f), 12)
            put(end, action("Новая игра") { reset(); openTab(0) })
            return
        }
        val quick = paddedCard(parent)
        put(quick, label("Быстрые действия", 18f, true), 12)
        put(quick, action("Открыть рынок") { openTab(2) }, 8)
        put(quick, action("Выбрать район", blue) { openTab(1) }, 8)
        put(quick, action("Финансы", Color.rgb(77, 70, 136)) { openTab(4) })
        val scoreCard = paddedCard(parent)
        put(scoreCard, label("Сыграно партий: $gamesFinished", 14f))
        if (bestScore != Long.MIN_VALUE) put(scoreCard, label("Лучший результат: ${money(bestScore)}", 14f, false, muted))
        put(scoreCard, action("Начать новую игру", Color.rgb(77, 70, 95)) {
            AlertDialog.Builder(this).setMessage("Начать новую игру и сбросить текущую партию?")
                .setPositiveButton("Начать") { _, _ -> reset(); openTab(0) }
                .setNegativeButton("Отмена", null).show()
        })
    }
    // Компактные строки сохраняют крупные зоны нажатия, но убирают лишнюю высоту карточек.
    private fun showMap(parent: LinearLayout) {
        header(parent, "Районы Харькова", "Переезд занимает 1 игровой день")
        if (ended) { put(parent, label("Партия завершена. Начни новую игру.")); return }
        places.forEachIndexed { i, name ->
            val here = i == place
            val card = paddedCard(parent)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = column()
            put(info, label((if (here) "●  " else "➜  ") + name, 16f, true, if (here) cyan else fg), 2)
            val note = when { here -> "Ты сейчас здесь"; i == 1 -> "Банк и ростовщик"; else -> "1 игровой день" }
            put(info, label(note, 12f, false, muted))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            if (!here) {
                row.addView(action("Ехать", blue) { travel(i) }, LinearLayout.LayoutParams(dp(86), -2).apply { marginStart = dp(8) })
            } else {
                row.addView(label("Здесь", 12f, true, cyan).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(86), dp(44)))
            }
            put(card, row)
        }
    }
    private fun showMarket(parent: LinearLayout) {
        header(parent, "Рынок — ${places[place]}", "Предложений: ${market.count { it }}")
        if (ended) { put(parent, label("Партия завершена.")); return }
        goods.indices.filter { market[it] || stock[it] > 0 }.forEach { i ->
            val card = paddedCard(parent)
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = column()
            put(info, label(goods[i].title, 16f, true), 2)
            put(info, label(if (market[i]) money(prices[i].toLong()) else "Нет в продаже", 16f, true, cyan), 2)
            val note = "В багаже: ${stock[i]} шт."
            put(info, label(note, 12f, false, muted), if (special[i] == 0 || !market[i]) 0 else 2)
            if (market[i] && special[i] != 0) {
                val event = if (special[i] < 0) "↓ Снижение цены" else "↑ Повышение цены"
                put(info, label(event, 12f, true, if (special[i] < 0) cyan else orange))
            }
            top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            if (market[i]) {
                val controls = column()
                controls.addView(action("Купить") { trade(i, true) }, LinearLayout.LayoutParams(dp(98), -2))
                if (stock[i] > 0) {
                    val sell = action("Продать", blue) { trade(i, false) }
                    controls.addView(sell, LinearLayout.LayoutParams(dp(98), -2).apply { topMargin = dp(5) })
                }
                top.addView(controls, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            }
            put(card, top)
        }
    }
    private fun showInventory(parent: LinearLayout) {
        header(parent, "Мой багаж", "Занято ${stock.sum()} из $capacity мест")
        val items = goods.indices.filter { stock[it] > 0 }
        val c = paddedCard(parent)
        if (items.isEmpty()) put(c, label("Багаж пока пуст", 16f, false, muted))
        items.forEach { i ->
            put(c, label("${goods[i].title}  ·  ${stock[i]} шт.", 16f, true), 10)
        }
        val upgrade = paddedCard(parent)
        put(upgrade, label("Увеличить багаж", 18f, true), 7)
        put(upgrade, label("Дополнительная сумка: +20 мест за ${money(1000)}. Это игровая адаптация.", 14f, false, muted), 12)
        put(upgrade, action("Купить дополнительную сумку") {
            if (cash >= 1000) {
                cash -= 1000; capacity += 20; message = "Вместимость багажа: $capacity"
                save(); render()
            } else Toast.makeText(this, "Недостаточно игровых денег", Toast.LENGTH_SHORT).show()
        })
    }
    private fun showFinance(parent: LinearLayout) {
        header(parent, "Финансы", "Банк и ростовщик находятся на ХТЗ")
        val summary = paddedCard(parent)
        put(summary, label("Наличные: ${money(cash)}", 18f, true), 10)
        put(summary, label("На счёте: ${money(bank)}", 17f, true, cyan), 10)
        put(summary, label("Текущий долг: ${money(debt)}", 17f, true, orange), 10)
        put(summary, label("При перемещении долг растёт на 10%, вклад — на 5%.", 13f, false, muted))
        if (ended) return
        if (place != 1) {
            val card = paddedCard(parent)
            put(card, label("Финансовые операции доступны на ХТЗ.", 15f), 10)
            put(card, action("Перейти к районам", blue) { openTab(1) })
            return
        }
        val actions = paddedCard(parent)
        put(actions, action("Погасить долг") { transfer("Погашение долга", min(cash, debt)) { amount -> cash -= amount; debt -= amount } }, 8)
        put(actions, action("Положить в банк", blue) { transfer("Вклад", cash) { amount -> cash -= amount; bank += amount } }, 8)
        put(actions, action("Снять со счёта", blue) { transfer("Снятие", bank) { amount -> bank -= amount; cash += amount } })
    }
    private fun numberDialog(title:String,max:Int,go:(Int)->Unit) {
        if(max<=0){Toast.makeText(this,"Недостаточно средств или товара",Toast.LENGTH_SHORT).show();return}
        val field=EditText(this);field.inputType=2;field.setText("1")
        AlertDialog.Builder(this).setTitle("$title (макс. $max)").setView(field)
            .setPositiveButton("Подтвердить") {_,_->val n=field.text.toString().toIntOrNull()?:0
                if(n in 1..max)go(n) else Toast.makeText(this,"Неверное количество",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Отмена",null).show()
    }
    /** Touch-friendly trade dialog. Only UI changed; trade accounting remains unchanged. */
    private fun trade(i:Int,buy:Boolean) {
        if (!market[i]) return
        val price = prices[i].toLong()
        val freeSpace = (capacity - stock.sum()).coerceAtLeast(0)
        val affordable = (cash / price).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val max = if (buy) min(freeSpace, affordable) else stock[i]
        if (max <= 0) {
            Toast.makeText(this, if (buy) "Не хватает денег или места в багаже" else "Нет товара для продажи", Toast.LENGTH_SHORT).show()
            return
        }

        val dp = resources.displayMetrics.density
        fun px(value: Int) = (value * dp + 0.5f).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(12), px(20), px(4))
        }
        val heading = label(goods[i].title, 22f, true, fg)
        box.addView(heading)
        box.addView(label("Цена за единицу: ${money(price)}", 16f, false, accent))
        box.addView(label(if (buy) "Наличные: ${money(cash)}  ·  Свободно мест: $freeSpace" else "У тебя: ${stock[i]} шт.", 14f))
        box.addView(label("Количество (от 1 до $max)", 14f))

        val qty = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            textSize = 24f
            setText("1")
            selectAll()
        }
        box.addView(qty, LinearLayout.LayoutParams(-1, px(54)))

        fun makeAdjustButton(title: String, action: () -> Unit): Button = button(title, action).apply {
            minWidth = 0
            minimumWidth = 0
            setPadding(px(3), 0, px(3), 0)
            textSize = 14f
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun current() = qty.text.toString().toIntOrNull() ?: 0
        fun setAmount(value: Int) { qty.setText(value.coerceIn(1, max).toString()); qty.setSelection(qty.text.length) }
        listOf<Pair<String, () -> Unit>>(
            "−10" to { setAmount(current() - 10) },
            "−1" to { setAmount(current() - 1) },
            "+1" to { setAmount(current() + 1) },
            "+10" to { setAmount(current() + 10) }
        ).forEach { (text, action) ->
            controls.addView(makeAdjustButton(text, action), LinearLayout.LayoutParams(0, px(48), 1f))
        }
        box.addView(controls)
        box.addView(makeAdjustButton(if (buy) "МАКСИМУМ ($max)" else "ПРОДАТЬ ВСЁ ($max)") { setAmount(max) },
            LinearLayout.LayoutParams(-1, px(48)))

        val total = label("", 19f, true, accent)
        box.addView(total)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (buy) "Покупка" else "Продажа")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton(if (buy) "Купить" else "Продать", null)
            .create()
        dialog.setOnShowListener {
            val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            fun update() {
                val value = qty.text.toString().toLongOrNull()
                val valid = value != null && value in 1L..max.toLong()
                confirm.isEnabled = valid
                total.text = when {
                    value == null -> "Укажи количество"
                    !valid -> "Введите число от 1 до $max"
                    else -> "${if (buy) "К оплате" else "Получишь"}: ${money(value * price)}"
                }
            }
            qty.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
                override fun afterTextChanged(s: Editable?) {}
            })
            confirm.setOnClickListener {
                val n = qty.text.toString().toIntOrNull() ?: return@setOnClickListener
                if (n !in 1..max) return@setOnClickListener
                val cost = n.toLong() * price
                if (buy) { cash -= cost; stock[i] += n } else { cash += cost; stock[i] -= n }
                message = "${if (buy) "Куплено" else "Продано"}: ${goods[i].title} × $n"
                save()
                dialog.dismiss()
                render()
            }
            update()
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun transfer(title:String,max:Long,go:(Long)->Unit) {
        numberDialog(title,min(max,Int.MAX_VALUE.toLong()).toInt()) {n->go(n.toLong());message="$title: ${money(n.toLong())}";save();render()}
    }
    private fun score(): Long = cash + bank - debt

    /** Story-only travel encounters, not a reproduction of the original NPC mechanics. */
    private fun rollTravelEvent(): String = when (rng.nextInt(10)) {
        0 -> "Попутчик рассказал странную историю о старом Харькове."
        1 -> "В метро объявили задержку, но ты благополучно добрался до района."
        2 -> "Уличные музыканты устроили небольшой концерт."
        3 -> "На улице сегодня необычайно многолюдно."
        else -> ""
    }

    private fun travel(target: Int) {
        if (target == place || ended) return
        AlertDialog.Builder(this).setTitle("Перемещение")
            .setMessage("Отправиться в район ${places[target]}? Пройдёт один игровой день.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Отправиться") { _, _ ->
                place = target; day++; debt = debt * 110 / 100; bank = bank * 105 / 100
                if (day > 30) {
                    ended = true; gamesFinished++
                    if (score() > bestScore) bestScore = score()
                    lastTripEvent = ""; message = "30 дней позади. Игра окончена."
                } else {
                    refreshPrices(); message = "Ты прибыл в район ${places[place]}. Цены обновились."
                    lastTripEvent = rollTravelEvent()
                }
                save(); render()
            }.show()
    }
}
