/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.Event
import com.salesforce.androidsdk.util.EventsObservable.EventType
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * This tracks activity events using a queue, allowing for tests to wait for certain events to turn up.
 */
class EventsListenerQueue : EventsObserver {

    abstract class BlockForEvent(val type: EventType) {
        abstract fun run(evt: Event)
    }

    private val events: BlockingQueue<Event> = ArrayBlockingQueue(10)
    private val blocks: MutableSet<BlockForEvent> = HashSet()

    init {
        EventsObservable.get().registerObserver(this)
    }

    override fun onEvent(evt: Event) {
        val matchingBlocks = mutableListOf<BlockForEvent>()
        for (block in blocks) {
            if (block.type == evt.type) {
                block.run(evt)
                matchingBlocks.add(block)
            }
        }
        blocks.removeAll(matchingBlocks)
        events.offer(evt)
    }

    /**
     * Register a block of code to run the next time a certain event is fired
     * @param block
     */
    fun registerBlock(block: BlockForEvent) {
        blocks.add(block)
    }

    fun tearDown() {
        EventsObservable.get().unregisterObserver(this)
    }

    // remove any events in the queue
    fun clearQueue() {
        events.clear()
    }

    /** will return the next event in the queue, waiting if needed for a reasonable amount of time */
    fun getNextEvent(): Event {
        return try {
            val e = events.poll(30, TimeUnit.SECONDS)
            e ?: throw RuntimeException("Failure ** Timeout waiting for an event ")
        } catch (ex: InterruptedException) {
            throw RuntimeException("Was interupted waiting for activity event")
        }
    }

    /** will wait for expected event in the queue, waiting till the timeout specified */
    fun waitForEvent(expectedType: EventType, timeout: Int): Event {
        val end = System.currentTimeMillis() + timeout
        var remaining = timeout.toLong()
        while (remaining > 0) {
            try {
                val e = events.poll(remaining, TimeUnit.MILLISECONDS)
                if (e != null && e.type == expectedType) {
                    return e
                }
            } catch (e: InterruptedException) {
                throw RuntimeException("Was interupted waiting for activity event")
            }
            remaining = end - System.currentTimeMillis()
        }
        throw RuntimeException("Failure ** Timeout waiting for an event ")
    }

    fun peekEvent(): Boolean {
        return events.peek() == null
    }
}
