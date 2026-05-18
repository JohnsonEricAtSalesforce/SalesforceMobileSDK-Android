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
package com.salesforce.samples.appconfigurator.ui

import android.app.Activity
import android.app.Fragment
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.RestrictionsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.salesforce.samples.appconfigurator.AppConfiguratorAdminReceiver
import com.salesforce.samples.appconfigurator.AppConfiguratorState
import com.salesforce.samples.appconfigurator.R

/**
 * This fragment provides UI and functionality to configure target application
 * sample.
 */
class ConfigureAppFragment : Fragment(), View.OnClickListener {

    // UI Components
    private lateinit var mTextStatus: TextView
    private lateinit var mTextXmlValues: TextView
    private lateinit var mButtonSave: Button
    private lateinit var mButtonShowXml: Button
    private lateinit var mLoginServers: EditText
    private lateinit var mLoginServersLabels: EditText
    private lateinit var mRemoteAccessConsumerKey: EditText
    private lateinit var mOauthRedirectURI: EditText
    private lateinit var mCertAlias: EditText
    private lateinit var mEditTexts: Array<EditText>
    private lateinit var mOnlyShowAuthorizedHosts: CheckBox
    private lateinit var mIDPAppURLScheme: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_configure_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mTextStatus = view.findViewById(R.id.status)
        mLoginServers = view.findViewById(R.id.login_servers)
        mLoginServersLabels = view.findViewById(R.id.login_servers_labels)
        mRemoteAccessConsumerKey = view.findViewById(R.id.remote_access_consumer_key)
        mOauthRedirectURI = view.findViewById(R.id.oauth_redirect_uri)
        mCertAlias = view.findViewById(R.id.cert_alias)
        mButtonSave = view.findViewById(R.id.save)
        mButtonSave.setOnClickListener(this)
        mEditTexts = arrayOf(
            mLoginServers, mLoginServersLabels, mRemoteAccessConsumerKey,
            mOauthRedirectURI, mCertAlias
        )
        mTextXmlValues = view.findViewById(R.id.text_view_xml)
        mTextXmlValues.movementMethod = ScrollingMovementMethod()
        mButtonShowXml = view.findViewById(R.id.show_xml)
        mButtonShowXml.setOnClickListener(this)
        mOnlyShowAuthorizedHosts = view.findViewById(R.id.only_allowed_servers)
        mIDPAppURLScheme = view.findViewById(R.id.idp_app_url_scheme)
    }

    override fun onResume() {
        super.onResume()
        updateUi(activity)
    }

    override fun onClick(view: View) {
        val state = AppConfiguratorState.getInstance(activity)
        when (view.id) {
            R.id.save -> {
                val isCertAuthEnabled = !mCertAlias.text.isNullOrBlank()
                val showOnlyAllowedServers = mOnlyShowAuthorizedHosts.isChecked
                state.saveConfigurations(
                    activity,
                    mLoginServers.text.toString(),
                    mLoginServersLabels.text.toString(),
                    mRemoteAccessConsumerKey.text.toString(),
                    mOauthRedirectURI.text.toString(),
                    isCertAuthEnabled,
                    mCertAlias.text.toString(),
                    showOnlyAllowedServers,
                    mIDPAppURLScheme.text.toString()
                )
                Toast.makeText(activity, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            R.id.show_xml -> {
                val manager =
                    activity.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
                val restrictions = manager.getManifestRestrictions(state.targetApp)
                val stringBuilder = StringBuilder()
                for (re in restrictions) {
                    stringBuilder.append("$re\n")
                }
                mTextXmlValues.text = stringBuilder.toString()
            }
        }
    }

    private fun updateUi(activity: Activity) {
        val state = AppConfiguratorState.getInstance(activity)
        val packageManager = activity.packageManager
        var status = -1 // ready
        try {
            @Suppress("DEPRECATION")
            val info = packageManager.getApplicationInfo(state.targetApp, PackageManager.GET_UNINSTALLED_PACKAGES)
            val devicePolicyManager = activity.getSystemService(Activity.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) {
                // Need to reinstall the sample app
                status = R.string.status_need_reinstall
            } else if (devicePolicyManager.isApplicationHidden(
                    AppConfiguratorAdminReceiver.getComponentName(activity), state.targetApp
                )
            ) {
                // The app is installed but hidden in this profile
                status = R.string.status_not_activated
            }
        } catch (e: PackageManager.NameNotFoundException) {
            status = R.string.status_not_installed
        }

        if (status < 0) {
            mLoginServers.setText(state.loginServers)
            mLoginServersLabels.setText(state.loginServersLabels)
            mRemoteAccessConsumerKey.setText(state.remoteAccessConsumerKey)
            mOauthRedirectURI.setText(state.oauthRedirectURI)
            mCertAlias.setText(state.certAlias)
            mTextStatus.visibility = View.GONE
            for (editText in mEditTexts) {
                editText.visibility = View.VISIBLE
            }
            mButtonSave.visibility = View.VISIBLE
            mButtonShowXml.visibility = View.VISIBLE
            mOnlyShowAuthorizedHosts.isChecked = state.shouldOnlyShowAuthorizedHosts()
            mIDPAppURLScheme.setText(state.idpAppURLScheme)
        } else {
            mTextStatus.setText(status)
            mTextStatus.visibility = View.VISIBLE
            for (editText in mEditTexts) {
                editText.visibility = View.GONE
            }
            mButtonSave.visibility = View.GONE
            mButtonShowXml.visibility = View.GONE
            mOnlyShowAuthorizedHosts.isChecked = false
        }
    }
}
