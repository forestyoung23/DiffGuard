package com.commitdiffaireview.context

/**
 * 方法调用信息
 * @param qualifier 调用对象（如 userMapper、redisTemplate）
 * @param methodName 方法名（如 insert、set）
 * @param callType 调用类型：MAPPER、REDIS、FEIGN、HTTP、THREAD、UNKNOWN
 */
data class MethodCall(
    val qualifier: String,
    val methodName: String,
    val callType: String
) {
    companion object {
        const val MAPPER = "MAPPER"
        const val REDIS = "REDIS"
        const val FEIGN = "FEIGN"
        const val HTTP = "HTTP"
        const val THREAD = "THREAD"
        const val UNKNOWN = "UNKNOWN"
    }
}

/**
 * 方法级别上下文
 * @param methodName 方法名
 * @param signature 方法签名（如 createUser(UserDTO dto)）
 * @param returnType 返回值类型
 * @param annotations 方法注解列表
 * @param methodCalls 方法体内的调用列表
 */
data class MethodContext(
    val methodName: String,
    val signature: String,
    val returnType: String,
    val annotations: List<String>,
    val methodCalls: List<MethodCall>
)
