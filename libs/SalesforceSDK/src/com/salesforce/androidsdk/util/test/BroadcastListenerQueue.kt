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
package com.salesforce.androidsdk.util.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This tracks broadcasts using a queue, allowing for tests to wait for certain broadcast to turn up.
 */
class BroadcastListenerQueue : BroadcastReceiver() {

    private val broadcasts = ArrayBlockingQueue<Intent>(10)

    override fun onReceive(context: Context, intent: Intent) {
        broadcasts.offer(intent)
    }

    /** Remove any events in the queue */
    fun clearQueue() {
        broadcasts.clear()
    }

    /** Will return the next event in the queue, waiting if needed for a reasonable amount of time */
    fun getNextBroadcast(): Intent {
        try {
            val broadcast = broadcasts.poll(30, TimeUnit.SECONDS)
                ?: throw RuntimeException("Failure ** Timeout waiting for a broadcast ")
            return broadcast
        } catch (ex: InterruptedException) {
            throw RuntimeException("Was interrupted waiting for broadcast")
        }
    }
}
