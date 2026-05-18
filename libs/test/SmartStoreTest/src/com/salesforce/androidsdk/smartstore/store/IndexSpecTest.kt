/*
 * Copyright (c) 2014-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.smartstore.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for IndexSpec
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class IndexSpecTest {

    companion object {
        private val keyStringSpec = IndexSpec("key", Type.string)
        private val keyIntegerSpec = IndexSpec("key", Type.integer)
        private val keyFloatingSpec = IndexSpec("key", Type.floating)
        private val keyFullTextSpec = IndexSpec("key", Type.full_text)
        private val keyJSON1Spec = IndexSpec("key", Type.json1)

        private val keyStringSpecWithCol = IndexSpec("key", Type.string, "COL_1")
        private val keyIntegerSpecWithCol = IndexSpec("key", Type.integer, "COL_1")
        private val keyFloatingSpecWithCol = IndexSpec("key", Type.floating, "COL_1")
        private val keyFullTextSpecWithCol = IndexSpec("key", Type.full_text, "COL_1")
        private val keyJSON1SpecWithCol = IndexSpec("key", Type.json1, "COL_1")
    }

    @Test
    fun testEqualsWithSame() {
        Assert.assertEquals(keyStringSpec, IndexSpec("key", Type.string))
        Assert.assertEquals(keyIntegerSpec, IndexSpec("key", Type.integer))
        Assert.assertEquals(keyFloatingSpec, IndexSpec("key", Type.floating))
        Assert.assertEquals(keyFullTextSpec, IndexSpec("key", Type.full_text))
        Assert.assertEquals(keyJSON1Spec, IndexSpec("key", Type.json1))
        Assert.assertEquals(keyStringSpecWithCol, IndexSpec("key", Type.string, "COL_1"))
        Assert.assertEquals(keyIntegerSpecWithCol, IndexSpec("key", Type.integer, "COL_1"))
        Assert.assertEquals(keyFloatingSpecWithCol, IndexSpec("key", Type.floating, "COL_1"))
        Assert.assertEquals(keyFullTextSpecWithCol, IndexSpec("key", Type.full_text, "COL_1"))
        Assert.assertEquals(keyJSON1SpecWithCol, IndexSpec("key", Type.json1, "COL_1"))
    }

    @Test
    fun testEqualsWithDifferent() {
        // Different path
        Assert.assertFalse(keyStringSpec == IndexSpec("otherKey", Type.string))
        // Different type
        Assert.assertFalse(keyStringSpec == IndexSpec("key", Type.integer))
        Assert.assertFalse(keyStringSpec == IndexSpec("key", Type.floating))
        Assert.assertFalse(keyStringSpec == IndexSpec("key", Type.full_text))
        Assert.assertFalse(keyStringSpec == IndexSpec("key", Type.json1))
        // Different columnName
        Assert.assertFalse(keyStringSpec == IndexSpec("key", Type.string, "COL_1"))
        Assert.assertFalse(keyStringSpecWithCol == IndexSpec("key", Type.string))
        Assert.assertFalse(keyStringSpecWithCol == IndexSpec("key", Type.string, "COL_2"))
    }

    @Test
    fun testHashCodeWithSame() {
        Assert.assertEquals(keyStringSpec.hashCode(), IndexSpec("key", Type.string).hashCode())
        Assert.assertEquals(keyIntegerSpec.hashCode(), IndexSpec("key", Type.integer).hashCode())
        Assert.assertEquals(keyFloatingSpec.hashCode(), IndexSpec("key", Type.floating).hashCode())
        Assert.assertEquals(keyFullTextSpec.hashCode(), IndexSpec("key", Type.full_text).hashCode())
        Assert.assertEquals(keyJSON1Spec.hashCode(), IndexSpec("key", Type.json1).hashCode())
        Assert.assertEquals(keyStringSpecWithCol.hashCode(), IndexSpec("key", Type.string, "COL_1").hashCode())
        Assert.assertEquals(keyIntegerSpecWithCol.hashCode(), IndexSpec("key", Type.integer, "COL_1").hashCode())
        Assert.assertEquals(keyFloatingSpecWithCol.hashCode(), IndexSpec("key", Type.floating, "COL_1").hashCode())
        Assert.assertEquals(keyFullTextSpecWithCol.hashCode(), IndexSpec("key", Type.full_text, "COL_1").hashCode())
        Assert.assertEquals(keyJSON1SpecWithCol.hashCode(), IndexSpec("key", Type.json1, "COL_1").hashCode())
    }

    @Test
    fun testHashCodeWithDifferent() {
        // Different path
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("otherKey", Type.string).hashCode())
        // Different type
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("key", Type.integer).hashCode())
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("key", Type.floating).hashCode())
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("key", Type.full_text).hashCode())
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("key", Type.json1).hashCode())
        // Different columnName
        Assert.assertFalse(keyStringSpec.hashCode() == IndexSpec("key", Type.string, "COL_1").hashCode())
        Assert.assertFalse(keyStringSpecWithCol.hashCode() == IndexSpec("key", Type.string).hashCode())
        Assert.assertFalse(keyStringSpecWithCol.hashCode() == IndexSpec("key", Type.string, "COL_2").hashCode())
    }
}
