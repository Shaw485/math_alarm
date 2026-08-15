package com.mira.mathalarm.math

import kotlin.random.Random

/**
 * 数学题数据类
 * 题目形式：a × b + c × d = ?
 */
data class MathProblem(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int
) {
    /** 正确答案 */
    val answer: Int
        get() = a * b + c * d

    /** 纯题目公式，运算符前后有空格（不含=?，供UI分两排渲染用） */
    val formula: String
        get() = "$a × $b + $c × $d"

    /** 完整题目显示文本（公式+等号问号连成一行） */
    val displayText: String
        get() = "$formula = ?"

    /**
     * 校验用户输入的答案是否正确
     * @param userAnswer 用户输入的字符串
     * @return 正确返回 true，错误或非法输入返回 false
     */
    fun checkAnswer(userAnswer: String): Boolean {
        return try {
            userAnswer.trim().toIntOrNull() == answer
        } catch (e: Exception) {
            false
        }
    }

    init {
        require(a in 3..9) { "a 必须在 3-9 范围内，当前值: $a" }
        require(b in 3..9) { "b 必须在 3-9 范围内，当前值: $b" }
        require(c in 3..9) { "c 必须在 3-9 范围内，当前值: $c" }
        require(d in 3..9) { "d 必须在 3-9 范围内，当前值: $d" }
    }
}

/**
 * 数学题生成器
 * 按 PRD 5.12 规则生成 a×b+c×d 形式的题目，a/b/c/d 均为 3-9 整数
 */
object MathProblemGenerator {

    private val random = Random.Default

    /**
     * 生成一道随机数学题
     * a、b、c、d 各自在 3-9 范围内独立随机取值
     */
    fun generate(): MathProblem {
        val a = random.nextInt(3, 10)
        val b = random.nextInt(3, 10)
        val c = random.nextInt(3, 10)
        val d = random.nextInt(3, 10)
        return MathProblem(a, b, c, d)
    }

    /**
     * 校验用户答案是否正确
     * @param problem 数学题
     * @param userAnswer 用户输入的答案字符串
     * @return 是否答对
     */
    fun checkAnswer(problem: MathProblem, userAnswer: String): Boolean {
        val answerInt = userAnswer.toIntOrNull() ?: return false
        return answerInt == problem.answer
    }

    /**
     * 校验用户答案是否正确（整数输入）
     */
    fun checkAnswer(problem: MathProblem, userAnswer: Int): Boolean {
        return userAnswer == problem.answer
    }
}
