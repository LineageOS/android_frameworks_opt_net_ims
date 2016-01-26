/*
 * Copyright (c) 2015, Motorola Mobility LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     - Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     - Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     - Neither the name of Motorola Mobility nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL MOTOROLA MOBILITY LLC BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */

package com.android.service.ims.presence;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import com.android.internal.telephony.Phone;
import android.provider.Settings;
import android.os.SystemProperties;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import android.telecom.TelecomManager;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.os.SystemClock;

import com.android.ims.ImsManager;
import com.android.ims.ImsConfig;
import com.android.ims.ImsConfig.FeatureConstants;
import com.android.ims.ImsConfig.FeatureValueConstants;
import com.android.service.ims.RcsSettingUtils;

import com.android.ims.RcsPresenceInfo;
import com.android.ims.IRcsPresenceListener;
import com.android.ims.RcsManager.ResultCode;
import com.android.ims.RcsPresence.PublishState;

import com.android.ims.internal.Logger;
import com.android.service.ims.TaskManager;
import com.android.service.ims.Task;

import com.android.ims.internal.uce.presence.PresPublishTriggerType;
import com.android.ims.internal.uce.presence.PresSipResponse;
import com.android.ims.internal.uce.common.StatusCode;
import com.android.ims.internal.uce.presence.PresCmdStatus;

import com.android.service.ims.RcsStackAdaptor;

import com.android.service.ims.R;

public class PresencePublication extends PresenceBase {
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     *  Publisher Error base
     */
    public static final int PUBLISH_ERROR_CODE_START = ResultCode.SUBSCRIBER_ERROR_CODE_END;

    /**
     * All publish errors not covered by specific errors
     */
    public static final int PUBLISH_GENIRIC_FAILURE = PUBLISH_ERROR_CODE_START - 1;

    /**
     * Responding for 403 - not authorized
     */
    public static final int PUBLISH_NOT_AUTHORIZED_FOR_PRESENCE = PUBLISH_ERROR_CODE_START - 2;

    /**
     * Responding for 404 error code. The subscriber is not provisioned.
     * The Client should not send any EAB traffic after get this error.
     */
    public static final int PUBLISH_NOT_PROVISIONED = PUBLISH_ERROR_CODE_START - 3;

    /**
     * Responding for 200 - OK
     */
    public static final int PUBLISH_SUCESS = ResultCode.SUCCESS;

    private RcsStackAdaptor mRcsStackAdaptor = null;
    private PresenceSubscriber mSubscriber = null;
    static private PresencePublication sPresencePublication = null;
    private BroadcastReceiver mReceiver = null;

    private PresenceCapability mMyCap = new PresenceCapability();

    private boolean mNetworkTypeLTE;
    private boolean mNetworkVoPSEnabled = true;

    private boolean mHasCachedTrigger = false;
    private boolean mGotTriggerFromStack = false;
    private boolean mDonotRetryUntilPowerCycle = false;
    private boolean mSimLoaded = false;
    private int mPreferredTtyMode = Phone.TTY_MODE_OFF;

    private boolean mVtEnabled = false;
    private boolean mDataEnabled = false;

    public class PublishType{
        public static final int PRES_PUBLISH_TRIGGER_DATA_CHANGED = 0;
        public static final int PRES_PUBLISH_TRIGGER_VTCALL_CHANGED = 1;
        public static final int PRES_PUBLISH_TRIGGER_VOIP_ENABLE_STATUS = 2;
        public static final int PRES_PUBLISH_TRIGGER_CACHED_TRIGGER = 3;
        public static final int PRES_PUBLISH_TRIGGER_TTY_ENABLE_STATUS = 4;
        public static final int PRES_PUBLISH_TRIGGER_RETRY = 5;
    };

    public class PresenceCapability {
        private boolean mIPVoiceSupported = true;
        private boolean mIPVideoSupported = true;

        public String myNumUri;

        public boolean isIPVoiceSupported() {
            if(!RcsSettingUtils.isVolteProvisioned(mContext)) {
                logger.print("isVolteProvisioned()=false");
                return false;
            }

            if (!mNetworkTypeLTE || !mNetworkVoPSEnabled) {
                logger.print("mNetworkTypeLTE=" + mNetworkTypeLTE +
                        " mNetworkVoPSEnabled=" + mNetworkVoPSEnabled);
                return false;
            }

            if(!ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext)) {
                logger.print("volte is not enabled.");
                return false;
            }

            return mIPVoiceSupported;
        }

        public void setIPVoiceSupported(boolean mIPVoiceSupported) {
            this.mIPVoiceSupported = mIPVoiceSupported;
        }

        public boolean isIPVideoSupported() {
            boolean videoSupported = false;
            logger.print("mVtEnabled=" + mVtEnabled + " mDataEnabled=" + mDataEnabled);

            if(!RcsSettingUtils.isLvcProvisioned(mContext)) {
                logger.print("isLvcProvisioned()=false");
                return false;
            }

            if(isTtyOn()){
                videoSupported = false;
                logger.print("isTtyOn=true, videoSupported=false");
                return videoSupported;
            }

            if (mVtEnabled && mDataEnabled && mNetworkVoPSEnabled) {
                logger.print( "Video is supported 1 and DATA is ON from NV");
                videoSupported = true;
            } else {
                logger.print( "Video is not supported and DATA is OFF from NV");
                videoSupported = false;
            }

            if(!ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mContext)) {
                videoSupported = false;
            }

            if (!mNetworkTypeLTE) {
                logger.print("networkType is not LTE");
                videoSupported = false;
            }
            return videoSupported;
        }

        public boolean isTtyOn() {
            logger.debug("isTtyOn settingsTtyMode=" + mPreferredTtyMode);
            return isTtyEnabled(mPreferredTtyMode);
        }
    }

    /**
     * @param rcsStackAdaptor
     * @param context
     */
    public PresencePublication(RcsStackAdaptor rcsStackAdaptor, Context context) {
        super();
        logger.debug("PresencePublication constrcuct");
        this.mRcsStackAdaptor = rcsStackAdaptor;
        this.mContext = context;

        mVtEnabled = ImsManager.isVtEnabledByUser(mContext);
        mDataEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.MOBILE_DATA, 1) == 1;
        mPreferredTtyMode = Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE,
                Phone.TTY_MODE_OFF);
        logger.debug("The current TTY mode is: " + mPreferredTtyMode);

        if(mRcsStackAdaptor != null){
            mRcsStackAdaptor.setPublishState(
                    SystemProperties.getInt("rcs.publish.status",
                    PublishState.PUBLISH_STATE_NOT_PUBLISHED));
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                logger.print("statusReceiver.onReceive intent=" + intent);
                if(TelephonyIntents.ACTION_SIM_STATE_CHANGED.equalsIgnoreCase(intent.getAction())){
                    String stateExtra = intent.getStringExtra(
                            IccCardConstants.INTENT_KEY_ICC_STATE);
                    logger.print("ACTION_SIM_STATE_CHANGED INTENT_KEY_ICC_STATE=" + stateExtra);
                    if(IccCardConstants.INTENT_VALUE_ICC_LOADED.equalsIgnoreCase(stateExtra)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                TelephonyManager teleMgr = TelephonyManager.getDefault();
                                if(teleMgr == null){
                                    logger.error("teleMgr = null");
                                    return;
                                }

                                int count = 0;
                                // retry 2 minutes to get the information from ISIM and RUIM
                                for(int i=0; i<60; i++) {
                                     String[] myImpu = teleMgr.getIsimImpu();
                                     String myDomain = teleMgr.getIsimDomain();
                                     String line1Number = teleMgr.getLine1Number();
                                     logger.debug("myImpu=" + myImpu + " myDomain=" + myDomain +
                                              " line1Number=" + line1Number);
                                     if(line1Number != null && line1Number.length() != 0 ||
                                         myImpu != null && myImpu.length != 0 &&
                                         myDomain != null && myDomain.length() != 0){
                                         mSimLoaded = true;
                                         // treate hot SIM hot swap as power on.
                                         mDonotRetryUntilPowerCycle = false;
                                         if(mHasCachedTrigger) {
                                             invokePublish(PresencePublication.PublishType.
                                                     PRES_PUBLISH_TRIGGER_CACHED_TRIGGER);
                                         }
                                         break;
                                     }else{
                                         try{
                                             Thread.sleep(2000);//retry 2 seconds later
                                         }catch(InterruptedException e){
                                         }
                                     }
                                 }
                            }
                        }, "wait for ISIM and RUIM").start();
                    }else if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.
                            equalsIgnoreCase(stateExtra)) {
                        // pulled out the SIM, set it as the same as power on status:
                        logger.print("Pulled out SIM, set to PUBLISH_STATE_NOT_PUBLISHED");

                        // only reset when the SIM gets absent.
                        mSimLoaded = false;
                        setPublishState(
                                PublishState.PUBLISH_STATE_NOT_PUBLISHED);
                    }
                }else if(Intent.ACTION_AIRPLANE_MODE_CHANGED.equalsIgnoreCase(intent.getAction())){
                    boolean airplaneMode = intent.getBooleanExtra("state", false);
                    if(airplaneMode){
                        logger.print("Airplane mode, set to PUBLISH_STATE_NOT_PUBLISHED");
                        setPublishState(
                                PublishState.PUBLISH_STATE_NOT_PUBLISHED);
                    }
                }else if(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED.equalsIgnoreCase(
                        intent.getAction())){
                    int newPreferredTtyMode = intent.getIntExtra(
                            TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                            TelecomManager.TTY_MODE_OFF);
                    newPreferredTtyMode = telecomTtyModeToPhoneMode(newPreferredTtyMode);
                    logger.debug("Tty mode changed from " + mPreferredTtyMode
                            + " to " + newPreferredTtyMode);

                    boolean mIsTtyEnabled = isTtyEnabled(mPreferredTtyMode);
                    boolean isTtyEnabled = isTtyEnabled(newPreferredTtyMode);
                    mPreferredTtyMode = newPreferredTtyMode;
                    if (mIsTtyEnabled != isTtyEnabled) {
                        logger.print("ttyEnabled status changed from " + mIsTtyEnabled
                                + " to " + isTtyEnabled);
                        invokePublish(PresencePublication.PublishType.
                                PRES_PUBLISH_TRIGGER_TTY_ENABLE_STATUS);
                    }
                } else if(ImsConfig.ACTION_IMS_FEATURE_CHANGED.equalsIgnoreCase(
                        intent.getAction())){
                    handleProvisionChanged();
                }
            }
        };

        IntentFilter statusFilter = new IntentFilter();
        statusFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        statusFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        statusFilter.addAction(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        statusFilter.addAction(ImsConfig.ACTION_IMS_FEATURE_CHANGED);
        mContext.registerReceiver(mReceiver, statusFilter);

        sPresencePublication = this;
    }

    private void handleProvisionChanged() {
        if(RcsSettingUtils.isEabProvisioned(mContext)) {
            logger.debug("provisioned, set mDonotRetryUntilPowerCycle to false");
            mDonotRetryUntilPowerCycle = false;
            if(mHasCachedTrigger) {
                invokePublish(PresencePublication.PublishType.PRES_PUBLISH_TRIGGER_CACHED_TRIGGER);
            }
        }
    }

    static public PresencePublication getPresencePublication() {
        return sPresencePublication;
    }

    public void setSubscriber(PresenceSubscriber subscriber) {
        mSubscriber = subscriber;
    }

    public boolean isDataEnabled() {
        return  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MOBILE_DATA, 1) == 1;
    }

    public void onMobileDataChanged(boolean value){
        logger.print("onMobileDataChanged, mDataEnabled=" + mDataEnabled + " value=" + value);
        if(mDataEnabled != value) {
            mDataEnabled = value;
            RcsSettingUtils.setMobileDataEnabled(mContext, mDataEnabled);

            invokePublish(
                    PresencePublication.PublishType.PRES_PUBLISH_TRIGGER_DATA_CHANGED);
        }
    }

    public void onVtEnabled(boolean enabled) {
        logger.debug("onVtEnabled mVtEnabled=" + mVtEnabled + " enabled=" + enabled);

        if(mVtEnabled != enabled) {
            mVtEnabled = enabled;
            invokePublish(PresencePublication.PublishType.
                    PRES_PUBLISH_TRIGGER_VTCALL_CHANGED);
        }
    }

    /**
     * @return the Publish State
     */
    public int getPublishState() {
        if(mRcsStackAdaptor == null){
            return PublishState.PUBLISH_STATE_NOT_PUBLISHED;
        }

        return mRcsStackAdaptor.getPublishState();
    }

    /**
     * @param mPublishState the publishState to set
     */
    public void setPublishState(int publishState) {
        if(mRcsStackAdaptor != null){
            mRcsStackAdaptor.setPublishState(publishState);
        }
    }

    public boolean getHasCachedTrigger(){
        return mHasCachedTrigger;
    }

    // Tiggered by framework.
    public int invokePublish(int trigger){
        int ret;

        long sleepTime = 0;
        switch(trigger)
        {
            case PublishType.PRES_PUBLISH_TRIGGER_DATA_CHANGED:
            {
                sleepTime = 300;
                logger.print("PRES_PUBLISH_TRIGGER_DATA_CHANGED");
                break;
            }
            case PublishType.PRES_PUBLISH_TRIGGER_VTCALL_CHANGED:
            {
                sleepTime = 300;
                logger.print("PRES_PUBLISH_TRIGGER_VTCALL_CHANGED");
                break;
            }
            case PublishType.PRES_PUBLISH_TRIGGER_CACHED_TRIGGER:
            {
                logger.print("PRES_PUBLISH_TRIGGER_CACHED_TRIGGER");
                break;
            }
            case PublishType.PRES_PUBLISH_TRIGGER_TTY_ENABLE_STATUS:
            {
                logger.print("PRES_PUBLISH_TRIGGER_TTY_ENABLE_STATUS");
                break;
            }
            case PublishType.PRES_PUBLISH_TRIGGER_VOIP_ENABLE_STATUS:
            {
                sleepTime = 300;
                logger.print("PRES_PUBLISH_TRIGGER_VOIP_ENABLE_STATUS");
                break;
            }
            case PublishType.PRES_PUBLISH_TRIGGER_RETRY:
            {
                logger.print("PRES_PUBLISH_TRIGGER_RETRY");
                break;
            }
            default:
            {
                logger.print("Unknown publish trigger from AP");
            }
        }

        try {
            if(sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            logger.debug("failed to sleep");
        }

        if(mGotTriggerFromStack == false){
            // The value mNetworkTypeLTE, mNetworkVoPSEnabled is not correct
            // if there is no trigger from stack yet.
            logger.print("Didn't get trigger from stack yet, discard framework trigger.");
            return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
        }

        if (mDonotRetryUntilPowerCycle) {
            logger.print("Don't publish until next power cycle");
            return ResultCode.SUCCESS;
        }

        if(!mSimLoaded){
            //need to read some information from SIM to publish
            logger.print("invokePublish cache the trigger since the SIM is not ready");
            mHasCachedTrigger = true;
            return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
        }

        //the provision status didn't be read from modem yet
        if(!RcsSettingUtils.isEabProvisioned(mContext)) {
            logger.print("invokePublish cache the trigger, not provision yet");
            mHasCachedTrigger = true;
            return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
        }

        ret = requestPublication();

        if(ret == ResultCode.ERROR_SERVICE_NOT_AVAILABLE){
            mHasCachedTrigger = true;
        }else{
            //reset the cached trigger
            mHasCachedTrigger = false;
        }

        return ret;
    }

    public int invokePublish(PresPublishTriggerType val) {
        int ret;
        switch (val.getPublishTrigeerType())
        {
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_ETAG_EXPIRED:
            {
                logger.print("PUBLISH_TRIGGER_ETAG_EXPIRED");
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_LTE_VOPS_DISABLED:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_LTE_VOPS_DISABLED");
                mNetworkTypeLTE = true;
                mNetworkVoPSEnabled = false;
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_LTE_VOPS_ENABLED:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_LTE_VOPS_ENABLED");
                mNetworkTypeLTE = true;
                mNetworkVoPSEnabled = true;
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_EHRPD:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_EHRPD");
                mNetworkTypeLTE = false;
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_HSPAPLUS:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_HSPAPLUS");
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_2G:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_2G");
                break;
            }
            case PresPublishTriggerType.UCE_PRES_PUBLISH_TRIGGER_MOVE_TO_3G:
            {
                logger.print("PUBLISH_TRIGGER_MOVE_TO_3G");
                break;
            }
            default:
                logger.print("Unknow Publish Trigger Type");
        }

        if (mDonotRetryUntilPowerCycle) {
            logger.print("Don't publish until next power cycle");
            return ResultCode.SUCCESS;
        }

        mGotTriggerFromStack = true;
        if(!mSimLoaded){
            //need to read some information from SIM to publish
            logger.print("invokePublish cache the trigger since the SIM is not ready");
            mHasCachedTrigger = true;
            return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
        }

        //the provision status didn't be read from modem yet
        if(!RcsSettingUtils.isEabProvisioned(mContext)) {
            logger.print("invokePublish cache the trigger, not provision yet");
            mHasCachedTrigger = true;
            return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
        }

        ret = requestPublication();

        if(ret == ResultCode.ERROR_SERVICE_NOT_AVAILABLE){
            mHasCachedTrigger = true;
        }else{
            //reset the cached trigger
            mHasCachedTrigger = false;
        }

        return ret;
    }

    private int requestPublication(){
        int ret = -1;

        if (mRcsStackAdaptor != null) {
            logger.debug("requestPublication");

            // "No response" had been handled by stack. So handle retry as per stack request only
            // The retry should be triggered by publish response
            // Since we are doing a publish, don't need the retry any more.
            mCancelRetry = true;
            if(mPendingRetry) {
                mPendingRetry = false;
                mAlarmManager.cancel(mRetryAlarmIntent);
            }

            TelephonyManager teleMgr = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);

            RcsPresenceInfo presenceInfo = new RcsPresenceInfo(teleMgr.getLine1Number(),
                    RcsPresenceInfo.VolteStatus.VOLTE_UNKNOWN,
                    mMyCap.isIPVoiceSupported()?RcsPresenceInfo.ServiceState.ONLINE:
                            RcsPresenceInfo.ServiceState.OFFLINE, null, System.currentTimeMillis(),
                    mMyCap.isIPVideoSupported()?RcsPresenceInfo.ServiceState.ONLINE:
                            RcsPresenceInfo.ServiceState.OFFLINE, null, System.currentTimeMillis());

            ret = mRcsStackAdaptor.requestPublication(presenceInfo, null);
        } else {
            logger.error("mRcsStackAdaptor = null");
        }

        return ret;
    }

    public void handleCmdStatus(PresCmdStatus cmdStatus) {
        super.handleCmdStatus(cmdStatus);
    }

    private PendingIntent mRetryAlarmIntent = null;
    public static final String ACTION_RETRY_PUBLISH_ALARM =
            "com.android.service.ims.presence.retry.publish";
    private AlarmManager mAlarmManager = null;
    private BroadcastReceiver mRetryReceiver = null;
    boolean mCancelRetry = true;
    boolean mPendingRetry = false;

    private void scheduleRetryPublish(int sipCode) {
        logger.print("sipCode=" + sipCode + " mPendingRetry=" + mPendingRetry +
                " mCancelRetry=" + mCancelRetry);

        // avoid duplicated retry.
        if(mPendingRetry) {
            logger.debug("There was a retry already");
            return;
        }
        mPendingRetry = true;
        mCancelRetry = false;

        Intent intent = new Intent(ACTION_RETRY_PUBLISH_ALARM);
        intent.putExtra("sipCode", sipCode);
        intent.setClass(mContext, AlarmBroadcastReceiver.class);
        mRetryAlarmIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        if(mAlarmManager == null) {
            mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        }

        //retry per 2 minutes
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 120000, mRetryAlarmIntent);
    }

    public void retryPublish(int sipCode) {
        logger.print("mCancelRetry=" + mCancelRetry);
        mPendingRetry = false;

        // Need some time to cancel it (1 minute for longest)
        // Don't do it if it was canceled already.
        if(mCancelRetry) {
            return;
        }

        invokePublish(PublishType.PRES_PUBLISH_TRIGGER_RETRY);
    }

    public void handleSipResponse(PresSipResponse pSipResponse) {
        logger.print( "Publish response code = " + pSipResponse.getSipResponseCode()
                +"Publish response reason phrase = " + pSipResponse.getReasonPhrase());

        if(pSipResponse == null){
            logger.debug("handlePublishResponse pSipResponse = null");
            return;
        }
        int sipCode = pSipResponse.getSipResponseCode();

        if(isInConfigList(sipCode, pSipResponse.getReasonPhrase(),
                R.array.config_volte_provision_error_on_publish_response)) {
            logger.print("volte provision error. sipCode=" + sipCode + " phrase=" +
                    pSipResponse.getReasonPhrase());
            setPublishState(PublishState.PUBLISH_STATE_VOLTE_PROVISION_ERROR);
            mDonotRetryUntilPowerCycle = true;

            notifyDm();

            return;
        }

        if(isInConfigList(sipCode, pSipResponse.getReasonPhrase(),
                R.array.config_rcs_provision_error_on_publish_response)) {
            logger.print("rcs provision error.sipCode=" + sipCode + " phrase=" +
                    pSipResponse.getReasonPhrase());
            setPublishState(PublishState.PUBLISH_STATE_RCS_PROVISION_ERROR);
            mDonotRetryUntilPowerCycle = true;

            return;
        }

        switch (sipCode) {
            case 999:
                logger.debug("Publish ignored - No capability change");
                break;
            case 200:
                setPublishState(PublishState.PUBLISH_STATE_200_OK);
                if(mSubscriber != null) {
                    mSubscriber.retryToGetAvailability();
                }
                break;

            case 408:
                setPublishState(PublishState.PUBLISH_STATE_REQUEST_TIMEOUT);
                break;

            default: // Generic Failure
                if ((sipCode < 100) || (sipCode > 699)) {
                    logger.debug("Ignore internal response code, sipCode=" + sipCode);
                    if(sipCode == 888) {
                        scheduleRetryPublish(sipCode);
                    } else {
                        logger.debug("Ignore internal response code, sipCode=" + sipCode);
                    }
                } else {
                    logger.debug( "Generic Failure");
                    setPublishState(PublishState.PUBLISH_STATE_OTHER_ERROR);

                    if ((sipCode>=400) && (sipCode <= 699)) {
                        // 4xx/5xx/6xx, No retry, no impact on subsequent publish
                        logger.debug( "No Retry in OEM");
                    }
                }
                break;
        }

        // Suppose the request ID had been set when IQPresListener_CMDStatus
        Task task = TaskManager.getDefault().getTaskByRequestId(
                pSipResponse.getRequestId());
        if(task != null){
            task.mSipResponseCode = pSipResponse.getSipResponseCode();
            task.mSipReasonPhrase = pSipResponse.getReasonPhrase();
        }

        handleCallback(task, getPublishState(), false);
    }

    private static boolean isTtyEnabled(int mode) {
        return Phone.TTY_MODE_OFF != mode;
    }

    private static int telecomTtyModeToPhoneMode(int telecomMode) {
        switch (telecomMode) {
            case TelecomManager.TTY_MODE_FULL:
                return Phone.TTY_MODE_FULL;
            case TelecomManager.TTY_MODE_VCO:
                return Phone.TTY_MODE_VCO;
            case TelecomManager.TTY_MODE_HCO:
                return Phone.TTY_MODE_HCO;
            case TelecomManager.TTY_MODE_OFF:
            default:
                return Phone.TTY_MODE_OFF;
        }
    }

    public void finish() {
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    protected void finalize() throws Throwable {
        finish();
    }
}
