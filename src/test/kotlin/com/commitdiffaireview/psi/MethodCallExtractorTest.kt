package com.commitdiffaireview.psi

import com.commitdiffaireview.context.MethodCall
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodCallExtractorTest : BasePlatformTestCase() {

    private val extractor = MethodCallExtractor()

    fun testDetectMapperCall() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                private UserMapper userMapper;
                public void createUser() {
                    userMapper.insert(new User());
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertEquals(1, calls.size)
        assertEquals("userMapper", calls[0].qualifier)
        assertEquals("insert", calls[0].methodName)
        assertEquals(MethodCall.MAPPER, calls[0].callType)
    }

    fun testDetectRedisCall() {
        val psiFile = myFixture.configureByText(
            "CacheService.java",
            """
            package com.demo;
            public class CacheService {
                private RedisTemplate redisTemplate;
                public void cacheValue() {
                    redisTemplate.opsForValue().set("key", "value");
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.REDIS })
    }

    fun testDetectThreadCall() {
        val psiFile = myFixture.configureByText(
            "AsyncService.java",
            """
            package com.demo;
            public class AsyncService {
                public void runAsync() {
                    new Thread(() -> {}).start();
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.THREAD })
    }

    fun testDetectHttpCallViaRestTemplate() {
        val psiFile = myFixture.configureByText(
            "ApiClient.java",
            """
            package com.demo;
            public class ApiClient {
                private RestTemplate restTemplate;
                public String callApi() {
                    return restTemplate.getForObject("http://example.com", String.class);
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.HTTP })
    }

    fun testDetectFeignClientCall() {
        val psiFile = myFixture.configureByText(
            "RemoteService.java",
            """
            package com.demo;
            public class RemoteService {
                private UserFeignClient userFeignClient;
                public void callRemote() {
                    userFeignClient.getUser(1L);
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.FEIGN })
    }

    fun testUnknownCallTypeForPlainMethod() {
        val psiFile = myFixture.configureByText(
            "Util.java",
            """
            package com.demo;
            public class Util {
                public void process() {
                    someHelper();
                }
                private String someHelper() { return ""; }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        // someHelper() has no qualifier, so it's UNKNOWN with empty qualifier
        assertTrue(calls.isNotEmpty())
        assertTrue(calls.all { it.callType == MethodCall.UNKNOWN })
    }

    fun testNoCallsInEmptyMethod() {
        val psiFile = myFixture.configureByText(
            "Empty.java",
            """
            package com.demo;
            public class Empty {
                public void doNothing() {
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.isEmpty())
    }
}
