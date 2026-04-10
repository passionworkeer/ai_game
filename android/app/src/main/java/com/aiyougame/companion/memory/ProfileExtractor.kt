package com.aiyougame.companion.memory

data class ProfileExtraction(
    val nickname: String? = null,
    val likes: String? = null,
    val dislikes: String? = null,
    val mood: String? = null,
    val keyEvent: KeyEventData? = null,
)

data class KeyEventData(
    val summary: String,
    val category: String,
    val importance: Int,
)

class ProfileExtractor {

    // ── compiled once at class-load time ──────────────────────────────────────
    private val nicknamePatterns = listOf(
        Regex("""(?:叫我|名字是|叫|喊).{0,8}?([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)(?:宝|贝|猪|狗|猫)"""),
        Regex("""([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)(?:宝|贝|猪|狗|猫)"""),
        Regex("""(?:叫我|名字是|叫|喊).*?([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)$"""),
    )
    private val likePatterns = listOf(
        Regex("""喜欢(?:吃|喝|玩|听|看)?.{0,15}?([\u4e00-\u9fa5a-zA-Z0-9，,。]+)"""),
        Regex("""(?:爱|最爱)(?:吃|喝|玩|听|看).{0,15}?([\u4e00-\u9fa5a-zA-Z0-9，,。]+)"""),
    )
    private val dislikePatterns = listOf(
        Regex("""讨厌.{0,5}?([\u4e00-\u9fa5a-zA-Z0-9，,。]+)"""),
        Regex("""不喜欢.{0,10}?([\u4e00-\u9fa5a-zA-Z0-9，,。]+)"""),
        Regex("""厌恶.{0,5}?([\u4e00-\u9fa5a-zA-Z0-9，,。]+)"""),
    )
    // longest-key first so compound phrases are matched before single chars
    private val moodMap: List<Pair<String, String>> = listOf(
        "压力大" to "stressed",
        "烦死了" to "stressed",
        "超好"   to "happy",
        "心情好" to "happy",
        "开心"   to "happy",
        "高兴"   to "happy",
        "快乐"   to "happy",
        "难过"   to "sad",
        "伤心"   to "sad",
        "郁闷"   to "sad",
        "生气"   to "angry",
        "愤怒"   to "angry",
        "好"     to "happy",
        "累"     to "stressed",
    )
    // birthday: two patterns - one with date number, one for bare "生日"
    private val birthdayPattern = Regex(
        """生日[的说是在,，\s]*([0-9零一二三四五六七八九十]{1,10}[月日号])"""
    )
    private val birthdayBarePattern = Regex("""生日""")
    private val promisePattern  = Regex("""(?:我们?|咱们)?[约好定排].{0,10}?([^\n，。！？]{2,20})""")
    private val activityPattern  = Regex("""(?:昨天|前天|上周).{0,5}?(?:看|去|吃|玩).{0,15}?([^\n，。！？]{2,20})""")
    private val jsonItemRegex   = Regex("""\"([^\"]+)\"""")

    /**
     * Parse a JSON array string like `["奶茶", "火锅"]` into a list of strings.
     */
    fun parseJsonArray(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return jsonItemRegex.findAll(json)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * Split a raw matched string by Chinese/full-width punctuation,
     * strip punctuation/whitespace from each token, and return non-blank ones.
     */
    private fun splitItems(raw: String): List<String> {
        val tokens = raw.split("，", "，", "。")
        return tokens
            .map { it.trim().filter { c -> c !in "，。　 \\t\n" } }
            .filter { it.isNotEmpty() }
    }

    fun extract(text: String): ProfileExtraction {
        // ── nickname ──────────────────────────────────────────────────────────
        var nickname: String? = null
        for (p in nicknamePatterns) {
            val match = p.find(text)
            if (match != null) {
                nickname = match.groupValues[1]
                break
            }
        }

        // ── likes ───────────────────────────────────────────────────────────────
        val likeItems = mutableListOf<String>()
        for (p in likePatterns) {
            for (m in p.findAll(text)) {
                likeItems.addAll(splitItems(m.groupValues[1]))
            }
        }

        // ── dislikes ───────────────────────────────────────────────────────────
        val dislikeItems = mutableListOf<String>()
        for (p in dislikePatterns) {
            for (m in p.findAll(text)) {
                dislikeItems.addAll(splitItems(m.groupValues[1]))
            }
        }

        // ── mood ───────────────────────────────────────────────────────────────
        var mood: String? = null
        for ((keyword, value) in moodMap) {
            if (text.contains(keyword)) { mood = value; break }
        }

        // ── keyEvent ───────────────────────────────────────────────────────────
        var keyEvent: KeyEventData? = null
        // Try birthday with date first, then fall back to bare "生日"
        birthdayPattern.find(text)?.let { m ->
            keyEvent = KeyEventData("生日${m.groupValues[1]}", "birthday", 5)
        }
        if (keyEvent == null && birthdayBarePattern.containsMatchIn(text)) {
            keyEvent = KeyEventData("生日", "birthday", 5)
        }
        if (keyEvent == null) {
            promisePattern.find(text)?.let { m ->
                keyEvent = KeyEventData("约${m.groupValues[1].take(10)}", "promise", 3)
            }
        }
        if (keyEvent == null) {
            activityPattern.find(text)?.let { m ->
                keyEvent = KeyEventData(m.groupValues[1].take(10), "activity", 2)
            }
        }

        val likesJson    = likeItems.distinct().takeIf { it.isNotEmpty() }
            ?.let { "[" + it.joinToString(",") { "\"$it\"" } + "]" }
        val dislikesJson = dislikeItems.distinct().takeIf { it.isNotEmpty() }
            ?.let { "[" + it.joinToString(",") { "\"$it\"" } + "]" }

        return ProfileExtraction(
            nickname = nickname,
            likes = likesJson,
            dislikes = dislikesJson,
            mood = mood,
            keyEvent = keyEvent,
        )
    }
}
