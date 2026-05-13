package com.commitdiffaireview.context

/**
 * 字段依赖信息
 * @param fieldName 字段名
 * @param typeName 类型全名
 * @param injectionType 注入方式：AUTOWIRED、RESOURCE、FINAL、NONE
 */
data class DependencyInfo(
    val fieldName: String,
    val typeName: String,
    val injectionType: String
) {
    companion object {
        const val AUTOWIRED = "AUTOWIRED"
        const val RESOURCE = "RESOURCE"
        const val FINAL = "FINAL"
        const val NONE = "NONE"
    }
}

/**
 * Spring 语义类型
 */
enum class SpringSemantic {
    SERVICE, CONTROLLER, REPOSITORY, COMPONENT, CONFIGURATION, NONE
}

/**
 * 类级别代码上下文
 * @param filePath 文件路径
 * @param packageName 包名
 * @param className 类名
 * @param superClass 父类（可选）
 * @param interfaces 实现的接口列表
 * @param annotations 类注解列表
 * @param dependencies 字段依赖列表
 * @param springSemantic Spring 语义
 * @param modifiedMethods 被修改的方法列表
 */
data class CodeContext(
    val filePath: String,
    val packageName: String,
    val className: String,
    val superClass: String?,
    val interfaces: List<String>,
    val annotations: List<String>,
    val dependencies: List<DependencyInfo>,
    val springSemantic: SpringSemantic,
    val modifiedMethods: List<MethodContext>
)
