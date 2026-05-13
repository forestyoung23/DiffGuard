package com.commitdiffaireview.analyzer

import com.commitdiffaireview.context.SpringSemantic
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod

/**
 * Spring 注解语义分析器
 * 识别类和方法上的 Spring 注解
 *
 * 由于 Spring 库不在 classpath 中，findAnnotation(qualifiedName) 无法解析注解全限定名。
 * 因此采用短名匹配 + import 验证的方式识别注解。
 */
class SpringSemanticAnalyzer {

    // 类级别注解短名到语义的映射
    private val classAnnotationShortNameMap = mapOf(
        "Service" to SpringSemantic.SERVICE,
        "Controller" to SpringSemantic.CONTROLLER,
        "RestController" to SpringSemantic.CONTROLLER,
        "Repository" to SpringSemantic.REPOSITORY,
        "Component" to SpringSemantic.COMPONENT,
        "Configuration" to SpringSemantic.CONFIGURATION
    )

    // 方法级别需要识别的注解短名集合
    private val methodAnnotationShortNames = setOf(
        "Transactional",
        "Async",
        "Scheduled",
        "Cacheable",
        "CacheEvict",
        "CachePut"
    )

    /**
     * 分析类的 Spring 语义
     * 按优先级匹配：RestController/Controller > Service > Repository > Component > Configuration > NONE
     */
    fun analyzeClass(psiClass: PsiClass): SpringSemantic {
        val modifiers = psiClass.modifierList ?: return SpringSemantic.NONE

        for (annotation in modifiers.annotations) {
            val shortName = annotation.nameReferenceElement?.referenceName ?: continue
            val semantic = classAnnotationShortNameMap[shortName]
            if (semantic != null) {
                return semantic
            }
        }

        return SpringSemantic.NONE
    }

    /**
     * 分析方法上的 Spring 注解，返回注解短名列表（如 ["@Transactional", "@Async"]）
     */
    fun analyzeMethodAnnotations(psiMethod: PsiMethod): List<String> {
        val modifiers = psiMethod.modifierList ?: return emptyList()

        return modifiers.annotations.mapNotNull { annotation ->
            val shortName = annotation.nameReferenceElement?.referenceName ?: return@mapNotNull null
            if (shortName in methodAnnotationShortNames) {
                "@$shortName"
            } else {
                null
            }
        }
    }
}
