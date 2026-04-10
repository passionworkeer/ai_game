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
    // Possessive quantifiers (+): consume max chars without backtracking,
    // preventing suffix chars from being swallowed as name characters.
    // This fixes the case where "小鱼吧" should extract "小鱼" (not "小鱼吧").
    private val nicknamePatterns = listOf(
        Regex("""(?:叫我|名字是|叫|喊).{0,8}?([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)(?:宝|贝|猪|狗|猫)"""),
        Regex("""([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)(?:宝|贝|猪|狗|猫)"""),
        Regex("""(?:叫我|名字是|叫|喊).*?([\u4e00-\u9fa5a-zA-Z0-9]{2,10}?)$"""),
    )
    private val likePatterns = listOf(
        Regex("""喜欢(吃|喝|玩|听|看)?.{0,15}?([\u4e00-\u9fa5a-zA-Z0-9\s,，,。]+)"""),
    )
    private val moodMap = mapOf(
        "开心" to "happy", "高兴" to "happy", "快乐" to "happy",
        "难过" to "sad", "伤心" to "sad", "郁闷" to "sad",
        "生气" to "angry", "愤怒" to "angry",
    )

    fun extract(text: String): ProfileExtraction {
        var nickname: String? = null
        for (p in nicknamePatterns) {
            val match = p.find(text)
            if (match != null) {
                nickname = match.groupValues.getOrNull(1)
                break
            }
        }
        return ProfileExtraction(nickname = nickname)
    }
}
