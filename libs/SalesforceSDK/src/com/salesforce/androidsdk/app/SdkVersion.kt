/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.app

import java.util.regex.Pattern

class SdkVersion @Throws(IllegalArgumentException::class) constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isDev: Boolean
) : Comparable<SdkVersion> {

    init {
        if (major < 0 || minor < 0 || patch < 0) {
            throw IllegalArgumentException(
                "Invalid version number combination: major=$major, minor=$minor, patch=$patch"
            )
        }
    }

    fun isGreaterThan(o: SdkVersion): Boolean {
        return this.compareTo(o) > 0
    }

    fun isGreaterThanOrEqualTo(o: SdkVersion): Boolean {
        return this.compareTo(o) >= 0
    }

    fun isLessThan(o: SdkVersion): Boolean {
        return this.compareTo(o) < 0
    }

    fun isLessThanOrEqualTo(o: SdkVersion): Boolean {
        return this.compareTo(o) <= 0
    }

    override fun compareTo(other: SdkVersion): Int {
        if (other === this) { // reference compare
            return 0
        }
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major)
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor)
        }
        if (this.patch != other.patch) {
            return Integer.compare(this.patch, other.patch)
        }
        if (this.isDev && !other.isDev) {
            return -1
        }
        if (!this.isDev && other.isDev) {
            return 1
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as SdkVersion

        if (major != that.major) return false
        if (minor != that.minor) return false
        if (patch != that.patch) return false
        return isDev == that.isDev
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + if (isDev) 1 else 0
        return result
    }

    override fun toString(): String {
        val builder = StringBuilder()
            .append(major)
            .append('.')
            .append(minor)
            .append('.')
            .append(patch)
        if (isDev) {
            builder.append(".dev")
        }
        return builder.toString()
    }

    companion object {
        /**
         * Matches version strings in the form of XX.YY.ZZ[.dev], where each version number can be 1-9 digits long.
         */
        private val VERSION_STR_PATTERN: Pattern = Pattern.compile("^\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}(\\.dev)?$")

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun parseFromString(versionStr: String): SdkVersion {
            val trimmed = versionStr.trim()
            if (!VERSION_STR_PATTERN.matcher(trimmed).matches()) {
                throw IllegalArgumentException(
                    "Version string \"$trimmed\" did not match expected pattern of XX.YY.ZZ[.dev]"
                )
            }

            val parts = trimmed.split(".")
            return SdkVersion(
                parts[0].toInt(),
                parts[1].toInt(),
                parts[2].toInt(),
                parts.size == 4 // If we pass regex matching then we know the final part is "dev"
            )
        }
    }
}
