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
                // После переезда сразу открываем рынок нового района.
                // По окончании партии показываем главный экран с результатом.
                activeTab = if (ended) 0 else 2
