package com.aiyougame.companion.llm

/**
 * 亲密度等级枚举，对应 UserProfileEntity.affectionLevel (0–100)。
 *
 * 注入时机：好感度每次更新后，下一条消息 Prompt 立即反映新等级，
 * 不需要重启模型。
 */
enum class IntimacyLevel(val range: IntRange) {
    /** 日常温柔：0–39，基础陪伴，偶尔手触 */
    DAILY(0..39),

    /** 暧昧升温：40–64，肢体亲近，情感试探 */
    FLIRTY(40..64),

    /** 确认心意：65–84，主动亲昵，说出在乎 */
    CLOSE(65..84),

    /** 深度陪伴：85–100，情感依赖，亲密无间 */
    DEEP(85..100);

    companion object {
        /**
         * 根据好感度分数返回对应等级。
         * 边界行为：score < 0 → DAILY，score > 100 → DEEP
         */
        fun from(score: Int): IntimacyLevel {
            val s = score.coerceIn(0, 100)
            return entries.first { s in it.range }
        }
    }
}
