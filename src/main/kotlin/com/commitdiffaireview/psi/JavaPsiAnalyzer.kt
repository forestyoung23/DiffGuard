package com.commitdiffaireview.psi

import com.commitdiffaireview.context.DependencyInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Java PSI 分析器
 * 负责从 PsiJavaFile 中提取类信息、字段依赖，以及定位修改的方法
 */
class JavaPsiAnalyzer {

    /**
     * 类级别信息
     */
    data class ClassInfo(
        val packageName: String,
        val className: String,
        val superClass: String?,
        val interfaces: List<String>,
        val annotations: List<String>
    )

    /**
     * 提取类的基本信息：包名、类名、父类、接口、注解
     */
    fun extractClassInfo(psiFile: PsiJavaFile, psiClass: PsiClass): ClassInfo {
        return ReadAction.compute<ClassInfo, Nothing> {
            val packageName = psiFile.packageName
            val className = psiClass.name ?: "Unknown"
            // 使用 extendsListTypes 而非 superClass，避免无法解析时返回 null
            val superClass = psiClass.extendsListTypes.firstOrNull()?.presentableText
            // 使用 implementsListTypes 避免接口无法解析时丢失信息
            val interfaces = psiClass.implementsListTypes.map { it.presentableText }
            val annotations = extractAnnotations(psiClass.modifierList)

            ClassInfo(
                packageName = packageName,
                className = className,
                superClass = superClass,
                interfaces = interfaces,
                annotations = annotations
            )
        }
    }

    /**
     * 提取字段依赖：@Autowired、@Resource、final field
     */
    fun extractDependencies(psiClass: PsiClass): List<DependencyInfo> {
        return ReadAction.compute<List<DependencyInfo>, Nothing> {
            psiClass.allFields.mapNotNull { field ->
                val modifiers = field.modifierList ?: return@mapNotNull null

                when {
                    // @Autowired 注入
                    hasAnnotation(modifiers, "Autowired") -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.AUTOWIRED
                        )
                    }
                    // @Resource 注入
                    hasAnnotation(modifiers, "Resource") -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.RESOURCE
                        )
                    }
                    // final 字段
                    modifiers.hasModifierProperty(PsiModifier.FINAL) -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.FINAL
                        )
                    }
                    else -> null
                }
            }
        }
    }

    /**
     * 查找包含修改行号的方法
     * @param psiClass 目标类
     * @param modifiedLines 修改的行号集合（1-based）
     * @return 包含修改行的方法列表
     */
    fun findModifiedMethods(psiClass: PsiClass, modifiedLines: Set<Int>): List<PsiMethod> {
        if (modifiedLines.isEmpty()) return emptyList()

        return ReadAction.compute<List<PsiMethod>, Nothing> {
            val allMethods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod::class.java)
            val document = psiClass.containingFile?.viewProvider?.document

            allMethods.filter { method ->
                val textRange = method.textRange ?: return@filter false
                val startLine = document?.getLineNumber(textRange.startOffset) ?: return@filter false
                val endLine = document?.getLineNumber(textRange.endOffset) ?: return@filter false

                // 行号从 0 开始，需要 +1 对齐到 1-based
                modifiedLines.any { line ->
                    line in (startLine + 1)..(endLine + 1)
                }
            }.toList()
        }
    }

    /**
     * 检查注解是否存在（使用短名匹配）
     */
    private fun hasAnnotation(modifiers: PsiModifierList, shortName: String): Boolean {
        return modifiers.annotations.any { annotation ->
            annotation.nameReferenceElement?.referenceName == shortName
        }
    }

    /**
     * 提取注解短名列表
     */
    private fun extractAnnotations(modifierList: PsiModifierList?): List<String> {
        if (modifierList == null) return emptyList()
        return modifierList.annotations.map { annotation ->
            annotation.nameReferenceElement?.referenceName ?: annotation.text
        }
    }
}
