package com.materialchat.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** The native generator resolves `invoke([F)Ljava/lang/Integer;`; a lambda cannot satisfy it. */
class SherpaSampleCallbackTest {
    @Test fun `declares the exact method descriptor the JNI layer looks up`() {
        val method = SherpaSampleCallback::class.java.getMethod("invoke", FloatArray::class.java)
        assertEquals(Integer::class.java, method.returnType)
    }

    @Test fun `continue and stop map to 1 and 0`() {
        assertEquals(1, SherpaSampleCallback { true }.invoke(floatArrayOf(0f)))
        assertEquals(0, SherpaSampleCallback { false }.invoke(floatArrayOf(0f)))
    }

    @Test fun `a kotlin lambda lacks the descriptor, which is why the callback class exists`() {
        val lambda: (FloatArray) -> Int = { 1 }
        val exact = lambda.javaClass.methods.any {
            it.name == "invoke" && it.parameterTypes.contentEquals(arrayOf(FloatArray::class.java)) &&
                it.returnType == Integer::class.java
        }
        assertEquals(false, exact)
    }
}
