/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import android.util.Log
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests to compare speed of smartstore full-text-search indices with regular indices
 */
@RunWith(Parameterized::class)
@LargeTest
class SmartStoreFullTextSearchSpeedTest(
    @JvmField @Parameterized.Parameter(0) val testName: String,
    @JvmField @Parameterized.Parameter(1) val rowsPerAnimal: Int,
    @JvmField @Parameterized.Parameter(2) val matchingRowsPerAnimal: Int
) : SmartStoreTestCase() {

    override val encryptionKey: String
        get() = "test123"

    @Test
    fun test() {
        trySearch(rowsPerAnimal, matchingRowsPerAnimal)
    }

    private fun trySearch(rowsPerAnimal: Int, matchingRowsPerAnimal: Int) {
        val totalInsertTimeString = setupData(Type.string, rowsPerAnimal, matchingRowsPerAnimal)
        val avgQueryTimeString = queryData(Type.string, rowsPerAnimal, matchingRowsPerAnimal)
        store.dropAllSoups()
        val totalInsertTimeFullText = setupData(Type.full_text, rowsPerAnimal, matchingRowsPerAnimal)
        val avgQueryTimeFullText = queryData(Type.full_text, rowsPerAnimal, matchingRowsPerAnimal)
        store.dropAllSoups()
        Log.i(
            TAG, String.format(
                "Search rows=%d matchingRows=%d avgQueryTimeString=%.4fs avgQueryTimeFullText=%.4fs (%.2f%%) totalInsertTimeString=%.3fs totalInsertTimeFullText=%.3fs (%.2f%%)",
                rowsPerAnimal * 25,
                matchingRowsPerAnimal,
                avgQueryTimeString,
                avgQueryTimeFullText,
                100 * avgQueryTimeFullText / avgQueryTimeString,
                totalInsertTimeString,
                totalInsertTimeFullText,
                100 * totalInsertTimeFullText / totalInsertTimeString
            )
        )
    }

    /**
     * @return total insert time in seconds
     */
    private fun setupData(textFieldType: Type, rowsPerAnimal: Int, matchingRowsPerAnimal: Int): Double {
        var totalInsertTime: Long = 0
        store.registerSoup(ANIMALS_SOUP, arrayOf(IndexSpec(TEXT_COL, textFieldType)))
        try {
            store.beginTransaction()
            for (i in 0 until 25) {
                val charToMatch = i + 'a'.code
                for (j in 0 until rowsPerAnimal) {
                    val prefix = String.format("%07d", j % (rowsPerAnimal / matchingRowsPerAnimal))
                    val text = StringBuilder()
                    for (animal in ANIMALS) {
                        if (animal[0].code == charToMatch) {
                            text.append(prefix).append(animal).append(" ")
                        }
                    }
                    val elt = JSONObject()
                    elt.put(TEXT_COL, text.toString())
                    val start = System.nanoTime()
                    store.create(ANIMALS_SOUP, elt, false)!!
                    totalInsertTime += System.nanoTime() - start
                }
            }
            store.setTransactionSuccessful()
        } finally {
            store.endTransaction()
        }
        return nanosToSeconds(totalInsertTime)
    }

    /**
     * @return avg query time in seconds
     */
    private fun queryData(textFieldType: Type, rowsPerAnimal: Int, matchingRowsPerAnimal: Int): Double {
        var totalQueryTime: Long = 0
        for (animal in ANIMALS) {
            val prefix = String.format("%07d", (Math.random() * (rowsPerAnimal / matchingRowsPerAnimal)).toInt())
            val stringToMatch = prefix + animal
            val querySpec = if (textFieldType == Type.full_text)
                QuerySpec.buildMatchQuerySpec(ANIMALS_SOUP, TEXT_COL, stringToMatch, null, null, rowsPerAnimal)
            else
                QuerySpec.buildLikeQuerySpec(ANIMALS_SOUP, TEXT_COL, "%$stringToMatch%", null, null, rowsPerAnimal)
            val start = System.nanoTime()
            val results = store.query(querySpec, 0)!!
            totalQueryTime += System.nanoTime() - start
            validateResults(matchingRowsPerAnimal, stringToMatch, results)
        }
        return nanosToSeconds(totalQueryTime) / ANIMALS.size
    }

    private fun validateResults(expectedRows: Int, stringToMatch: String, results: org.json.JSONArray) {
        Assert.assertEquals("Wrong number of results", expectedRows, results.length())
        for (i in 0 until results.length()) {
            val text = results.getJSONObject(i).getString(TEXT_COL)
            Assert.assertTrue("Invalid result [$text] for search on [$stringToMatch]", text.contains(stringToMatch))
        }
    }

    private fun nanosToSeconds(nanos: Long): Double {
        return nanos / 1000000000.0
    }

    companion object {
        const val TAG = "SmartStoreFTSSpeedTest"

        // Animals A..Y
        val ANIMALS = arrayOf(
            "alligator", "ant", "bear", "bee", "bird", "camel", "cat",
            "cheetah", "chicken", "chimpanzee", "cow", "crocodile", "deer", "dog", "dolphin",
            "duck", "eagle", "elephant", "fish", "fly", "fox", "frog", "giraffe", "goat",
            "goldfish", "hamster", "hippopotamus", "horse", "iguana", "impala", "jaguar", "jellyfish", "kangaroo", "kitten", "lion",
            "lobster", "monkey", "nightingale", "octopus", "owl", "panda", "pig", "puppy", "quail", "rabbit", "rat",
            "scorpion", "seal", "shark", "sheep", "snail", "snake", "spider", "squirrel",
            "tiger", "turtle", "umbrellabird", "vulture", "wolf", "xantus", "xerus", "yak"
        )

        const val ANIMALS_SOUP = "animals"
        const val TEXT_COL = "text"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("Search1000RowsOneMatch", 40, 1),
                arrayOf("Search1000RowsManyMatches", 40, 40),
                arrayOf("Search10000RowsOneMatch", 400, 1),
                arrayOf("Search10000RowsManyMatches", 400, 400) //,
                // arrayOf("testSearch100000RowsOneMatch", 4000, 1) // Slow - uncomment when collecting performance data
            )
        }
    }
}
