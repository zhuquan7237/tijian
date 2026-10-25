package com.zhuquan.tijian

import com.zhuquan.tijian.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** tag → versionCode 推导，自更新比较的基准。 */
class VersionMathTest {

    @Test fun tripleVersion() {
        assertEquals(8090200, Updater.semverCode("8.9.2"))
    }

    @Test fun majorBumpBeatsMinor() {
        assertTrue(Updater.semverCode("9.0.0")!! > Updater.semverCode("8.99.99")!!)
    }

    @Test fun doubleVersionPadsZero() {
        assertEquals(9000000, Updater.semverCode("9.0"))
    }

    @Test fun versionPrefixAloneIsRejected() {
        // 调用方 fromApi 已先 removePrefix("v")，裸的 v8.9.2 不放行
        assertNull(Updater.semverCode("v8.9.2"))
    }

    @Test fun garbageIsNull() {
        assertNull(Updater.semverCode("latest"))
        assertNull(Updater.semverCode(""))
    }
}
