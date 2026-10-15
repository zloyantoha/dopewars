package com.dopewars.ru

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
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
    private fun label(txt: String, size: Float = 16f, bold: Boolean = false, color: Int = fg): TextView = TextView(this).apply {
        text=txt; textSize=size;setTextColor(color);setPadding(0, 7, 0, 7)
        if(bold) setTypeface(null, Typeface.BOLD)
    }
    private fun button(txt: String, run: () -> Unit): Button = Button(this).apply {
        text=txt;setTextColor(Color.WHITE);setAllCaps(false); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43, 82, 104)))
        setOnClickListener { run() }
    }
    private fun section(): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(20,16,20,16);setBackgroundColor(panel)
        val p=LinearLayout.LayoutParams(-1,-2);p.bottomMargin=14;root.addView(this,p)
    }
    private fun render() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(18,18,18,30);setBackgroundColor(bg) }
        val sc=ScrollView(this);sc.isFillViewport = true;sc.addView(root);setContentView(sc)
        root.addView(label("DOPEWARS  ·  RU", 25f, true, accent))
        root.addView(label("День $day / 30     •     ${places[place]}", 17f, true))
        val info=section()
        info.addView(label("Наличные: ${money(cash)}",20f,true))
        info.addView(label("Долг: ${money(debt)}     Банк: ${money(bank)}"))
        info.addView(label("Багаж: ${stock.sum()} / $capacity · свободно ${capacity - stock.sum()}", 14f))
        info.addView(label("Завершено партий: $gamesFinished" + if(bestScore != Long.MIN_VALUE) " · рекорд ${money(bestScore)}" else "", 13f))
        val news=section();news.addView(label(message,15f,false,accent))
        if(lastTripEvent.isNotBlank()) news.addView(label(lastTripEvent,14f))
        if (ended) {
            val card=section();card.addView(label("ИГРА ЗАВЕРШЕНА",22f,true))
            card.addView(label("Итог: ${money(score())}",20f,true,accent))
            card.addView(label("Наличные ${money(cash)}, банк ${money(bank)}, долг ${money(debt)}"))
            card.addView(label("Остатки багажа в итоговом счёте не оцениваются."))
            card.addView(label("Лучший результат: ${money(bestScore)}"))
            card.addView(button("Новая игра") {reset();render()});return
        }
        val marketCard=section();marketCard.addView(label("РЫНОК · ${market.count { it }} предложений",20f,true))
        for(i in goods.indices) {
            if (!market[i] && stock[i]==0) continue
            val row=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(0,8,0,8)}
            val marker = when (special[i]) { -1 -> "  ↓ РАСПРОДАЖА"; 1 -> "  ↑ ДЕФИЦИТ"; else -> "" }
            row.addView(label("${goods[i].title}   ${if(market[i]) money(prices[i].toLong()) else "нет в продаже"}   •   у тебя: ${stock[i]}$marker",15f,true))
            val actions=LinearLayout(this)
            if(market[i]) actions.addView(button("Купить") {trade(i,true)})
            if(stock[i]>0 && market[i]) actions.addView(button("Продать") {trade(i,false)})
            row.addView(actions);marketCard.addView(row)
        }
        val capacityCard=section();capacityCard.addView(label("БАГАЖ",20f,true))
        capacityCard.addView(label("Вместимость: $capacity единиц. Расширение багажа — игровая адаптация, не оригинальная формула.",13f))
        capacityCard.addView(button("Дополнительная сумка (+20 мест) · $1 000") {
            if(cash>=1000){cash-=1000;capacity+=20;message="Дополнительная сумка куплена. Вместимость: $capacity";save();render()}
            else Toast.makeText(this,"Не хватает игровых денег",Toast.LENGTH_SHORT).show()
        })
        val navigate=section();navigate.addView(label("ПЕРЕМЕЩЕНИЕ",20f,true))
        navigate.addView(button("Выбрать район") { choosePlace() })
        val finance=section();finance.addView(label("ФИНАНСЫ",20f,true))
        finance.addView(label("Банк и ростовщик находятся на ХТЗ.",13f))
        if(place==1) {
            finance.addView(button("Погасить долг") { transfer("Погашение долга", min(cash,debt)) { amt -> cash-=amt;debt-=amt } })
            finance.addView(button("Положить в банк") {transfer("Вклад",cash) {amt->cash-=amt;bank+=amt}})
            finance.addView(button("Снять со счёта") {transfer("Снятие",bank) {amt->bank-=amt;cash+=amt}})
        }
        root.addView(button("Начать новую игру") {AlertDialog.Builder(this).setMessage("Сбросить весь прогресс?")
            .setPositiveButton("Да") {_,_->reset();render()}.setNegativeButton("Отмена",null).show()})
    }
    private fun numberDialog(title:String,max:Int,go:(Int)->Unit) {
        if(max<=0){Toast.makeText(this,"Недостаточно средств или товара",Toast.LENGTH_SHORT).show();return}
        val field=EditText(this);field.inputType=2;field.setText("1")
        AlertDialog.Builder(this).setTitle("$title (макс. $max)").setView(field)
            .setPositiveButton("Подтвердить") {_,_->val n=field.text.toString().toIntOrNull()?:0
                if(n in 1..max)go(n) else Toast.makeText(this,"Неверное количество",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Отмена",null).show()
    }
    private fun trade(i:Int,buy:Boolean) {
        if(!market[i])return
        val max=if(buy) min(capacity-stock.sum(),(cash/prices[i]).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else stock[i]
        numberDialog("${if(buy) "Купить" else "Продать"}: ${goods[i].title}",max) { n ->
            val cost=n.toLong()*prices[i]
            if(buy){cash-=cost;stock[i]+=n}else{cash+=cost;stock[i]-=n}
            message="${if(buy) "Куплено" else "Продано"}: ${goods[i].title} × $n";save();render()
        }
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

    private fun choosePlace() {
        val choices=places.mapIndexedNotNull {i,s->if(i!=place)s else null}.toTypedArray()
        val ids=places.indices.filter {it!=place}
        AlertDialog.Builder(this).setTitle("Куда отправимся?").setItems(choices) {_,which->
            place=ids[which];day++;debt=debt*110/100;bank=bank*105/100
            if(day>30){
                ended=true; gamesFinished++
                if(score()>bestScore) bestScore=score()
                lastTripEvent=""
                message="30 дней позади. Игра окончена."
            } else {
                refreshPrices();message="Ты прибыл в район ${places[place]}. Цены обновились."
                lastTripEvent=rollTravelEvent()
            }
            save();render()
        }.setNegativeButton("Отмена",null).show()
    }
}
