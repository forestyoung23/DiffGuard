package dev.diffguard.analyzer

import dev.diffguard.context.SpringSemantic
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpringSemanticAnalyzerTest : BasePlatformTestCase() {

    private val analyzer = SpringSemanticAnalyzer()

    fun testClassWithServiceAnnotation() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.SERVICE, semantic)
    }

    fun testClassWithControllerAnnotation() {
        val psiFile = myFixture.configureByText(
            "UserController.java",
            """
            package com.demo;
            import org.springframework.stereotype.Controller;
            @Controller
            public class UserController {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.CONTROLLER, semantic)
    }

    fun testClassWithRestControllerAnnotation() {
        val psiFile = myFixture.configureByText(
            "ApiController.java",
            """
            package com.demo;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class ApiController {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.CONTROLLER, semantic)
    }

    fun testClassWithRepositoryAnnotation() {
        val psiFile = myFixture.configureByText(
            "UserRepository.java",
            """
            package com.demo;
            import org.springframework.stereotype.Repository;
            @Repository
            public class UserRepository {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.REPOSITORY, semantic)
    }

    fun testClassWithoutSpringAnnotation() {
        val psiFile = myFixture.configureByText(
            "PlainClass.java",
            """
            package com.demo;
            public class PlainClass {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.NONE, semantic)
    }

    fun testMethodWithTransactionalAnnotation() {
        val psiFile = myFixture.configureByText(
            "Service.java",
            """
            package com.demo;
            import org.springframework.transaction.annotation.Transactional;
            public class Service {
                @Transactional
                public void doWork() {
                }
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val method = psiClass.methods[0]
        val annotations = analyzer.analyzeMethodAnnotations(method)

        assertTrue(annotations.contains("@Transactional"))
    }

    fun testMethodWithMultipleAnnotations() {
        val psiFile = myFixture.configureByText(
            "Service.java",
            """
            package com.demo;
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.scheduling.annotation.Async;
            public class Service {
                @Transactional
                @Async
                public void doWork() {
                }
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val method = psiClass.methods[0]
        val annotations = analyzer.analyzeMethodAnnotations(method)

        assertTrue(annotations.contains("@Transactional"))
        assertTrue(annotations.contains("@Async"))
    }
}
