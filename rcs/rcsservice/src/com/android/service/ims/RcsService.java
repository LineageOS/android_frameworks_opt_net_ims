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

package com.android.service.ims;

import android.net.Uri;

import java.util.List;

import android.content.Intent;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.content.Context;
import android.app.Service;
import android.os.ServiceManager;
import android.os.Handler;
import android.database.ContentObserver;
import android.content.BroadcastReceiver;
import android.provider.Settings;
import android.net.ConnectivityManager;
import com.android.ims.ImsConfig.FeatureValueConstants;
import com.android.ims.ImsManager;
import com.android.ims.ImsConfig;
import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsServiceClass;
import com.android.ims.ImsException;
import android.telephony.SubscriptionManager;

import com.android.ims.RcsManager.ResultCode;
import com.android.ims.internal.IRcsService;
import com.android.ims.IRcsPresenceListener;
import com.android.ims.internal.IRcsPresence;
import com.android.ims.RcsPresence.PublishState;

import com.android.ims.internal.Logger;
import com.android.service.ims.RcsStackAdaptor;

import com.android.service.ims.presence.PresencePublication;
import com.android.service.ims.presence.PresenceSubscriber;

public class RcsService extends Service{
    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    final static String ACTION_IMS_FEATURE_STATUS_CHANGED =
            "com.android.service.ims.presence.ims-feature-changed";
    private static final int INVALID_SERVICE_ID = -1;
    int mServiceId = INVALID_SERVICE_ID;

    private RcsStackAdaptor mRcsStackAdaptor = null;
    private PresencePublication mPublication = null;
    private PresenceSubscriber mSubscriber = null;
    private ImsManager mImsManager = null;

    @Override
    public void onCreate() {
        super.onCreate();

        logger.debug("RcsService onCreate");

        mRcsStackAdaptor = RcsStackAdaptor.getInstance(this);

        mPublication = new PresencePublication(mRcsStackAdaptor, this);
        mRcsStackAdaptor.getListener().setPresencePublication(mPublication);

        mSubscriber = new PresenceSubscriber(mRcsStackAdaptor, this);
        mRcsStackAdaptor.getListener().setPresenceSubscriber(mSubscriber);
        mPublication.setSubscriber(mSubscriber);

        ConnectivityManager cm = ConnectivityManager.from(this);
        if (cm != null) {
            boolean enabled = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.MOBILE_DATA, 1) == 1;
            logger.debug("Mobile data enabled status: " + (enabled ? "ON" : "OFF"));

            onMobileDataEnabled(enabled);
        }

        // TODO: support MSIM
        ServiceManager.addService("rcs", mBinder);

        mObserver = new MobileDataContentObserver();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                false, mObserver);

        mVtSettingObserver = new VtSettingContentObserver();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.VT_IMS_ENABLED),
                false, mVtSettingObserver);

        mImsManager = ImsManager.getInstance(this,
                SubscriptionManager.from(this).getDefaultDataPhoneId());

        registerImsConnectionStateListener();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("RcsService onStartCommand");

        return super.onStartCommand(intent, flags, startId);
    }

    /**
      * Cleans up when the service is destroyed
      */
    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(mObserver);
        getContentResolver().unregisterContentObserver(mVtSettingObserver);
        mRcsStackAdaptor.finish();
        mPublication.finish();
        mPublication = null;
        mSubscriber = null;

        logger.debug("RcsService onDestroy");
        super.onDestroy();
    }

    public PresencePublication getPublication() {
        return mPublication;
    }

    public PresenceSubscriber getPresenceSubscriber(){
        return mSubscriber;
    }

    IRcsPresence.Stub mIRcsPresenceImpl = new IRcsPresence.Stub(){
        /**
         * Asyncrhonously request the latest capability for a given contact list.
         * The result will be saved to DB directly if the contactNumber can be found in DB.
         * And then send intent com.android.ims.presence.CAPABILITY_STATE_CHANGED to notify it.
         * @param contactsNumber the contact list which will request capability.
         *                       Currently only support phone number.
         * @param listener the listener to get the response.
         * @return the resultCode which is defined by ResultCode.
         * @note framework uses only.
         * @hide
         */
        public int requestCapability(List<String> contactsNumber,
            IRcsPresenceListener listener){
            logger.debug("calling requestCapability");
            if(mSubscriber == null){
                logger.debug("requestCapability, mPresenceSubscriber == null");
                return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
            }

            return mSubscriber.requestCapability(contactsNumber, listener);
         }

        /**
         * Asyncrhonously request the latest presence for a given contact.
         * The result will be saved to DB directly if it can be found in DB. And then send intent
         * com.android.ims.presence.AVAILABILITY_STATE_CHANGED to notify it.
         * @param contactNumber the contact which will request available.
         *                       Currently only support phone number.
         * @param listener the listener to get the response.
         * @return the resultCode which is defined by ResultCode.
         * @note framework uses only.
         * @hide
         */
        public int requestAvailability(String contactNumber, IRcsPresenceListener listener){
            logger.debug("calling requestAvailability, contactNumber=" + contactNumber);
            if(mSubscriber == null){
                logger.error("requestAvailability, mPresenceSubscriber is null");
                return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
            }

            // check availability cache (in RAM).
            return mSubscriber.requestAvailability(contactNumber, listener, false);
        }

        /**
         * Same as requestAvailability. but requestAvailability will consider throttle to avoid too
         * fast call. Which means it will not send the request to network in next 60s for the same
         * request.
         * The error code SUBSCRIBE_TOO_FREQUENTLY will be returned under the case.
         * But for this funcation it will always send the request to network.
         *
         * @see IRcsPresenceListener
         * @see RcsManager.ResultCode
         * @see ResultCode.SUBSCRIBE_TOO_FREQUENTLY
         */
        public int requestAvailabilityNoThrottle(String contactNumber,
                IRcsPresenceListener listener) {
            logger.debug("calling requestAvailabilityNoThrottle, contactNumber=" + contactNumber);
            if(mSubscriber == null){
                logger.error("requestAvailabilityNoThrottle, mPresenceSubscriber is null");
                return ResultCode.ERROR_SERVICE_NOT_AVAILABLE;
            }

            // check availability cache (in RAM).
            return mSubscriber.requestAvailability(contactNumber, listener, true);
        }

        public int getPublishState() throws RemoteException {
            return mPublication.getPublishState();
        }
    };

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    /**
     * Receives notifications when Mobile data is enabled or disabled.
     */
    private class MobileDataContentObserver extends ContentObserver {
        public MobileDataContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(final boolean selfChange) {
            boolean enabled = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.MOBILE_DATA, 1) == 1;
            logger.debug("Mobile data enabled status: " + (enabled ? "ON" : "OFF"));
            onMobileDataEnabled(enabled);
        }
    }

    /** Observer to get notified when Mobile data enabled status changes */
    private MobileDataContentObserver mObserver;

    private void onMobileDataEnabled(final boolean enabled) {
        logger.debug("Enter onMobileDataEnabled: " + enabled);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    if(mPublication != null){
                        mPublication.onMobileDataChanged(enabled);
                        return;
                    }
                }catch(Exception e){
                    logger.error("Exception onMobileDataEnabled:", e);
                }
            }
        }, "onMobileDataEnabled thread");

        thread.start();
    }


    private VtSettingContentObserver mVtSettingObserver;

    /**
     * Receives notifications when Mobile data is enabled or disabled.
     */
    private class VtSettingContentObserver extends ContentObserver {
        public VtSettingContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(final boolean selfChange) {
            boolean enabled = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.VT_IMS_ENABLED, 1) == 1;
            logger.debug("vt enabled status: " + (enabled ? "ON" : "OFF"));

            onVtEnabled(enabled);
        }
    }

    private void onVtEnabled(boolean enabled) {
        if(mPublication != null){
            mPublication.onVtEnabled(enabled);
        }
    }

    private final IRcsService.Stub mBinder = new IRcsService.Stub() {
        /**
         * return true if the rcs service is ready for use.
         */
        public boolean isRcsServiceAvailable(){
            logger.debug("calling isRcsServiceAvailable");
            if(mRcsStackAdaptor == null){
                return false;
            }

            return mRcsStackAdaptor.isImsEnableState();
        }

        /**
         * Gets the presence interface.
         *
         * @see IRcsPresence
         */
        public IRcsPresence getRcsPresenceInterface(){
            return mIRcsPresenceImpl;
        }
    };

    void registerImsConnectionStateListener() {
        Thread t = new Thread() {
            @Override
            public void run() {
                while (mServiceId == INVALID_SERVICE_ID) {
                    try {
                        mServiceId = mImsManager.open(ImsServiceClass.MMTEL,
                                createIncomingCallPendingIntent(),
                                mImsConnectionStateListener);
                    } catch (ImsException e) {
                        logger.error("register exception=", e);
                    }

                    if (mServiceId == INVALID_SERVICE_ID) {
                        try {
                            logger.print("register wait for imsservice");
                            sleep(300);
                        } catch (InterruptedException e) {
                            logger.error("register exception=", e);
                        }
                    } else {
                        logger.print("register imsservice ready mServiceId="+mServiceId);
                    }
                }
            }
        };

        t.start();
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent(ACTION_IMS_FEATURE_STATUS_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private ImsConnectionStateListener mImsConnectionStateListener =
        new ImsConnectionStateListener() {
            @Override
            public void onImsConnected() {
                logger.debug("onImsConnected");
                if(mRcsStackAdaptor != null) {
                    mRcsStackAdaptor.checkSubService();
                }
            }
        };
}

