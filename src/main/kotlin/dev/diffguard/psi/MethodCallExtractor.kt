package dev.diffguard.psi

import dev.diffguard.context.MethodCall
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*

/**
 * 方法调用提取器
 * 递归遍历方法体，提取所有方法调用及其类型
 */
class MethodCallExtractor {

    // Mapper 相关的类型名关键词
    private val mapperKeywords = setOf("Mapper", "Dao", "Repository")

    // Redis 相关的类型名关键词
    private val redisKeywords = setOf("Redis", "redis", "RedisTemplate", "StringRedisTemplate", "Jedis")

    // Feign 相关的类型名关键词
    private val feignKeywords = setOf("Feign", "Client", "FeignClient")

    // HTTP 相关的方法名
    private val httpMethods = setOf(
        "getForObject", "getForEntity", "postForObject", "postForEntity",
        "put", "delete", "exchange", "patchForObject",
        "get", "post", "head", "options"
    )

    // Thread 相关的类型名关键词
    private val threadKeywords = setOf(
        "Thread", "Executor", "ExecutorService", "CompletableFuture",
        "ScheduledExecutorService", "ForkJoinPool"
    )

    /**
     * 提取方法体内的所有方法调用
     */
    fun extractCalls(psiMethod: PsiMethod): List<MethodCall> {
        return ReadAction.compute<List<MethodCall>, Nothing> {
            val calls = mutableListOf<MethodCall>()
            psiMethod.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    val call = analyzeCall(expression)
                    if (call != null) {
                        calls.add(call)
                    }
                }
            })
            calls
        }
    }

    /**
     * 分析单个方法调用，判断其类型
     */
    private fun analyzeCall(expression: PsiMethodCallExpression): MethodCall? {
        val methodName = expression.methodExpression.referenceName ?: return null
        val qualifier = expression.methodExpression.qualifierExpression?.text ?: ""

        val callType = classifyCall(qualifier, methodName, expression)

        return MethodCall(
            qualifier = qualifier,
            methodName = methodName,
            callType = callType
        )
    }

    /**
     * 根据 qualifier 和 methodName 判断调用类型
     */
    private fun classifyCall(qualifier: String, methodName: String, expression: PsiMethodCallExpression): String {
        // 1. 检查是否为 Thread 相关
        if (isThreadCall(qualifier, expression)) return MethodCall.THREAD

        // 2. 检查是否为 Mapper 调用
        if (isMapperCall(qualifier, expression)) return MethodCall.MAPPER

        // 3. 检查是否为 Redis 调用
        if (isRedisCall(qualifier, expression)) return MethodCall.REDIS

        // 4. 检查是否为 Feign 调用
        if (isFeignCall(qualifier, expression)) return MethodCall.FEIGN

        // 5. 检查是否为 HTTP 调用
        if (isHttpCall(qualifier, methodName)) return MethodCall.HTTP

        return MethodCall.UNKNOWN
    }

    private fun isMapperCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && mapperKeywords.any { qualifierType.contains(it) }) return true
        return mapperKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isRedisCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && redisKeywords.any { qualifierType.contains(it) }) return true
        return redisKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isFeignCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && feignKeywords.any { qualifierType.contains(it) }) return true
        return feignKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isHttpCall(qualifier: String, methodName: String): Boolean {
        if (httpMethods.contains(methodName) && qualifier.contains("restTemplate", ignoreCase = true)) return true
        if (httpMethods.contains(methodName) && qualifier.contains("webClient", ignoreCase = true)) return true
        return false
    }

    private fun isThreadCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        if (qualifier.contains("thread", ignoreCase = true) && expression.methodExpression.referenceName == "start") return true
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && threadKeywords.any { qualifierType.contains(it) }) return true
        return threadKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    /**
     * 尝试解析 qualifier 的类型名
     */
    private fun resolveQualifierType(expression: PsiMethodCallExpression): String? {
        val qualifier = expression.methodExpression.qualifierExpression ?: return null
        val type = (qualifier as? PsiReferenceExpression)?.type ?: return null
        return type.presentableText
    }
}
