package com.mira.mathalarm

import com.mira.mathalarm.math.MathProblem
import com.mira.mathalarm.math.MathProblemGenerator
import org.junit.Test
import org.junit.Assert.*

/**
 * 数学题生成与校验单元测试
 * 覆盖 PRD 5.12 数学题规则的核心逻辑
 */
class MathProblemGeneratorTest {

    /**
     * 测试生成的题目中 a/b/c/d 都在 3-9 范围内
     */
    @Test
    fun generate_numbersInRange() {
        repeat(100) {
            val problem = MathProblemGenerator.generate()
            assertTrue("a 应在 3-9 范围内，实际: ${problem.a}", problem.a in 3..9)
            assertTrue("b 应在 3-9 范围内，实际: ${problem.b}", problem.b in 3..9)
            assertTrue("c 应在 3-9 范围内，实际: ${problem.c}", problem.c in 3..9)
            assertTrue("d 应在 3-9 范围内，实际: ${problem.d}", problem.d in 3..9)
        }
    }

    /**
     * 测试答案计算是否正确（先乘后加）
     */
    @Test
    fun answer_calculationIsCorrect() {
        val problem = MathProblem(a = 3, b = 4, c = 5, d = 6)
        // 3*4 + 5*6 = 12 + 30 = 42
        assertEquals(42, problem.answer)
    }

    /**
     * 测试答案边界：最小值
     * a=b=c=d=3 时，答案 = 3*3 + 3*3 = 18
     */
    @Test
    fun answer_minimumValue() {
        val problem = MathProblem(a = 3, b = 3, c = 3, d = 3)
        assertEquals(18, problem.answer)
    }

    /**
     * 测试答案边界：最大值
     * a=b=c=d=9 时，答案 = 9*9 + 9*9 = 81 + 81 = 162
     */
    @Test
    fun answer_maximumValue() {
        val problem = MathProblem(a = 9, b = 9, c = 9, d = 9)
        assertEquals(162, problem.answer)
    }

    /**
     * 测试答案始终为正整数
     */
    @Test
    fun answer_isPositiveInteger() {
        repeat(100) {
            val problem = MathProblemGenerator.generate()
            assertTrue("答案应为正整数，实际: ${problem.answer}", problem.answer > 0)
            assertTrue("答案范围应为 18-162，实际: ${problem.answer}", problem.answer in 18..162)
        }
    }

    /**
     * 测试答案校验：正确答案返回 true
     */
    @Test
    fun checkAnswer_correctReturnsTrue() {
        val problem = MathProblem(a = 3, b = 4, c = 5, d = 6)
        // 3*4 + 5*6 = 12 + 30 = 42
        assertTrue(MathProblemGenerator.checkAnswer(problem, 42))
        assertTrue(MathProblemGenerator.checkAnswer(problem, "42"))
    }

    /**
     * 测试答案校验：错误答案返回 false
     */
    @Test
    fun checkAnswer_wrongReturnsFalse() {
        val problem = MathProblem(a = 3, b = 4, c = 5, d = 6)
        assertFalse(MathProblemGenerator.checkAnswer(problem, 41))
        assertFalse(MathProblemGenerator.checkAnswer(problem, "41"))
    }

    /**
     * 测试答案校验：空字符串返回 false
     */
    @Test
    fun checkAnswer_emptyStringReturnsFalse() {
        val problem = MathProblemGenerator.generate()
        assertFalse(MathProblemGenerator.checkAnswer(problem, ""))
    }

    /**
     * 测试答案校验：非数字字符串返回 false
     */
    @Test
    fun checkAnswer_nonNumericStringReturnsFalse() {
        val problem = MathProblemGenerator.generate()
        assertFalse(MathProblemGenerator.checkAnswer(problem, "abc"))
        assertFalse(MathProblemGenerator.checkAnswer(problem, "12a"))
    }

    /**
     * 测试答案校验：负数返回 false
     */
    @Test
    fun checkAnswer_negativeReturnsFalse() {
        val problem = MathProblemGenerator.generate()
        assertFalse(MathProblemGenerator.checkAnswer(problem, "-5"))
    }

    /**
     * 测试题目显示文本格式
     * 应使用 × 符号，运算符前后有空格
     */
    @Test
    fun displayText_formatIsCorrect() {
        val problem = MathProblem(a = 4, b = 4, c = 3, d = 3)
        assertEquals("4 × 4 + 3 × 3", problem.formula)
        assertEquals("4 × 4 + 3 × 3 = ?", problem.displayText)
    }

    /**
     * 测试题目显示文本包含半角问号
     */
    @Test
    fun displayText_endsWithHalfWidthQuestionMark() {
        val problem = MathProblemGenerator.generate()
        assertTrue(problem.displayText.endsWith("?"))
        assertFalse(problem.displayText.endsWith("？"))
    }

    /**
     * 测试运算顺序：先乘后加
     * 验证两次乘法之和不会被错误地按从左到右连续运算
     */
    @Test
    fun answer_followsOrderOfOperations() {
        // 测试 a=3, b=3, c=4, d=5
        // 正确: 3*3 + 4*5 = 9 + 20 = 29
        // 错误: (3*3 + 4)*5 = 13*5 = 65
        val problem = MathProblem(a = 3, b = 3, c = 4, d = 5)
        assertEquals(29, problem.answer)
        assertNotEquals(65, problem.answer)
    }

    /**
     * 测试 MathProblem 构造函数校验：a 超出范围抛异常
     */
    @Test(expected = IllegalArgumentException::class)
    fun problem_aOutOfRangeThrowsException() {
        MathProblem(a = 0, b = 3, c = 3, d = 3)
    }

    /**
     * 测试 MathProblem 构造函数校验：b 超出范围抛异常
     */
    @Test(expected = IllegalArgumentException::class)
    fun problem_bOutOfRangeThrowsException() {
        MathProblem(a = 3, b = 10, c = 3, d = 3)
    }

    /**
     * 测试生成的题目具有随机性（100 次生成不应完全相同）
     */
    @Test
    fun generate_isRandom() {
        val problems = mutableSetOf<String>()
        repeat(50) {
            problems.add(MathProblemGenerator.generate().displayText)
        }
        // 50 次生成应该至少有多个不同的题目
        assertTrue("生成的题目应具有随机性", problems.size > 5)
    }
}
