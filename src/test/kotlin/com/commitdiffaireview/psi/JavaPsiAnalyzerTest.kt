package com.commitdiffaireview.psi

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaPsiAnalyzerTest : BasePlatformTestCase() {

    private val analyzer = JavaPsiAnalyzer()

    fun testExtractClassInfoWithAnnotations() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo.service;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService extends BaseService implements IUserService {
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val info = analyzer.extractClassInfo(psiFile, psiClass)

        assertEquals("com.demo.service", info.packageName)
        assertEquals("UserService", info.className)
        assertEquals("BaseService", info.superClass)
        assertTrue(info.interfaces.contains("IUserService"))
        assertTrue(info.annotations.any { it.contains("Service") })
    }

    fun testExtractDependenciesWithAutowired() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            import org.springframework.beans.factory.annotation.Autowired;
            public class UserService {
                @Autowired
                private UserMapper userMapper;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("userMapper", deps[0].fieldName)
        assertEquals("UserMapper", deps[0].typeName)
        assertEquals("AUTOWIRED", deps[0].injectionType)
    }

    fun testExtractDependenciesWithResource() {
        val psiFile = myFixture.configureByText(
            "OrderService.java",
            """
            package com.demo;
            import javax.annotation.Resource;
            public class OrderService {
                @Resource
                private OrderMapper orderMapper;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("orderMapper", deps[0].fieldName)
        assertEquals("OrderMapper", deps[0].typeName)
        assertEquals("RESOURCE", deps[0].injectionType)
    }

    fun testExtractDependenciesWithFinalField() {
        val psiFile = myFixture.configureByText(
            "CacheService.java",
            """
            package com.demo;
            public class CacheService {
                private final RedisTemplate<String, String> redisTemplate;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("redisTemplate", deps[0].fieldName)
        assertEquals("FINAL", deps[0].injectionType)
    }

    fun testFindModifiedMethodsByLineNumber() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                public void methodA() {
                    // line 4
                }

                public void methodB() {
                    // line 8
                    System.out.println("modified");
                    // line 10
                }

                public void methodC() {
                    // line 13
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        // line 9 is inside methodB
        val methods = analyzer.findModifiedMethods(psiClass, setOf(9))

        assertEquals(1, methods.size)
        assertEquals("methodB", methods[0].name)
    }

    fun testFindMultipleModifiedMethods() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                public void methodA() {
                    System.out.println("a");
                }

                public void methodB() {
                    System.out.println("b");
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val methods = analyzer.findModifiedMethods(psiClass, setOf(4, 8))

        assertEquals(2, methods.size)
        val names = methods.map { it.name }.toSet()
        assertTrue(names.contains("methodA"))
        assertTrue(names.contains("methodB"))
    }

    fun testNoModifiedMethodsWhenLinesOutsideMethods() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                private String field;

                public void methodA() {
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        // line 4 is a field declaration, not inside a method
        val methods = analyzer.findModifiedMethods(psiClass, setOf(4))

        assertTrue(methods.isEmpty())
    }
}
