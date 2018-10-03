/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.ims;

import android.annotation.Nullable;
import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.IImsSmsListener;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.ImsSmsImplBase;
import android.util.Log;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.ims.internal.IImsUt;

/**
 * A container of the IImsServiceController binder, which implements all of the ImsFeatures that
 * the platform currently supports: MMTel and RCS.
 * @hide
 */

public class MmTelFeatureConnection {

    protected static final String TAG = "MmTelFeatureConnection";
    protected final int mSlotId;
    protected IBinder mBinder;
    private Context mContext;

    private volatile boolean mIsAvailable = false;
    // ImsFeature Status from the ImsService. Cached.
    private Integer mFeatureStateCached = null;
    private IFeatureUpdate mStatusCallback;
    private final Object mLock = new Object();
    // Updated by IImsServiceFeatureCallback when FEATURE_EMERGENCY_MMTEL is sent.
    private boolean mSupportsEmergencyCalling = false;

    // Cache the Registration and Config interfaces as long as the MmTel feature is connected. If
    // it becomes disconnected, invalidate.
    private IImsRegistration mRegistrationBinder;
    private IImsConfig mConfigBinder;

    private IBinder.DeathRecipient mDeathRecipient = () -> {
            Log.w(TAG, "DeathRecipient triggered, binder died.");
            onRemovedOrDied();
    };

    private abstract class CallbackAdapterManager<T extends IInterface> {
        private static final String TAG = "CallbackAdapterManager";

        protected final RemoteCallbackList<T> mRemoteCallbacks =
                new RemoteCallbackList<>();

        public void addCallback(T localCallback) throws RemoteException {
            synchronized (mLock) {
                // throws a RemoteException if a connection can not be established.
                if (!createConnection(localCallback)) {
                    throw new RemoteException("Can not create connection!");
                }
                Log.i(TAG, "Local callback added: " + localCallback);
                mRemoteCallbacks.register(localCallback);
            }
        }

        public void removeCallback(T localCallback) {
            Log.i(TAG, "Local callback removed: " + localCallback);
            synchronized (mLock) {
                removeConnection(localCallback);
                mRemoteCallbacks.unregister(localCallback);
            }
        }

        public void close() {
            synchronized (mLock) {
                final int lastCallbackIndex = mRemoteCallbacks.getRegisteredCallbackCount() - 1;
                for(int ii = lastCallbackIndex; ii >= 0; ii --) {
                    T callbackItem = mRemoteCallbacks.getRegisteredCallbackItem(ii);
                    removeConnection(callbackItem);
                    mRemoteCallbacks.unregister(callbackItem);
                }
                Log.i(TAG, "Closing connection and clearing callbacks");
            }
        }

        abstract boolean createConnection(T localCallback) throws RemoteException;

        abstract void removeConnection(T localCallback);
    }
    private ImsRegistrationCallbackAdapter mRegistrationCallbackManager
            = new ImsRegistrationCallbackAdapter();
    private class ImsRegistrationCallbackAdapter
            extends CallbackAdapterManager<ImsRegistrationImplBase.Callback> {

        @Override
        boolean createConnection(ImsRegistrationImplBase.Callback localCallback)
                throws RemoteException {
            IImsRegistration imsRegistration = getRegistration();
            if (imsRegistration != null) {
                getRegistration().addRegistrationCallback(localCallback);
                return true;
            } else {
                Log.e(TAG, "ImsRegistration is null");
                return false;
            }
        }

        @Override
        void removeConnection(ImsRegistrationImplBase.Callback localCallback) {
            IImsRegistration imsRegistration = getRegistration();
            if (imsRegistration != null) {
                try {
                    getRegistration().removeRegistrationCallback(localCallback);
                } catch (RemoteException e) {
                    Log.w(TAG, "removeConnection: couldn't remove registration callback");
                }
            } else {
                Log.e(TAG, "ImsRegistration is null");
            }
        }
    }

    private final CapabilityCallbackManager mCapabilityCallbackManager
            = new CapabilityCallbackManager();
    private class CapabilityCallbackManager
            extends CallbackAdapterManager<ImsFeature.CapabilityCallback> {

        @Override
        boolean createConnection(ImsFeature.CapabilityCallback localCallback) throws RemoteException {
            IImsMmTelFeature binder;
            synchronized (mLock) {
                checkServiceIsReady();
                binder = getServiceInterface(mBinder);
            }
            if (binder != null) {
                binder.addCapabilityCallback(localCallback);
                return true;
            } else {
                Log.w(TAG, "create: Couldn't get IImsMmTelFeature binder");
                return false;
            }
        }

        @Override
        void removeConnection(ImsFeature.CapabilityCallback localCallback) {
            IImsMmTelFeature binder = null;
            synchronized (mLock) {
                try {
                    checkServiceIsReady();
                    binder = getServiceInterface(mBinder);
                } catch (RemoteException e) {
                    // binder is null
                    Log.w(TAG, "remove: Binder is null");
                }
            }
            if (binder != null) {
                try {
                    binder.removeCapabilityCallback(localCallback);
                } catch (RemoteException e) {
                    Log.w(TAG, "remove: IImsMmTelFeature binder is dead");
                }
            } else {
                Log.w(TAG, "remove: Couldn't get IImsMmTelFeature binder");
            }
        }
    }


    public static MmTelFeatureConnection create(Context context , int slotId) {
        MmTelFeatureConnection serviceProxy = new MmTelFeatureConnection(context, slotId);

        TelephonyManager tm  = getTelephonyManager(context);
        if (tm == null) {
            Rlog.w(TAG, "create: TelephonyManager is null!");
            // Binder can be unset in this case because it will be torn down/recreated as part of
            // a retry mechanism until the serviceProxy binder is set successfully.
            return serviceProxy;
        }

        IImsMmTelFeature binder = tm.getImsMmTelFeatureAndListen(slotId,
                serviceProxy.getListener());
        if (binder != null) {
            serviceProxy.setBinder(binder.asBinder());
            // Trigger the cache to be updated for feature status.
            serviceProxy.getFeatureState();
        } else {
            Rlog.w(TAG, "create: binder is null! Slot Id: " + slotId);
        }
        return serviceProxy;
    }

    public static TelephonyManager getTelephonyManager(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public interface IFeatureUpdate {
        /**
         * Called when the ImsFeature has changed its state. Use
         * {@link ImsFeature#getFeatureState()} to get the new state.
         */
        void notifyStateChanged();

        /**
         * Called when the ImsFeature has become unavailable due to the binder switching or app
         * crashing. A new ImsServiceProxy should be requested for that feature.
         */
        void notifyUnavailable();
    }

    private final IImsServiceFeatureCallback mListenerBinder =
            new IImsServiceFeatureCallback.Stub() {

        @Override
        public void imsFeatureCreated(int slotId, int feature) throws RemoteException {
            // The feature has been enabled. This happens when the feature is first created and may
            // happen when the feature is re-enabled.
            synchronized (mLock) {
                if(mSlotId != slotId) {
                    return;
                }
                switch (feature) {
                    case ImsFeature.FEATURE_MMTEL: {
                        if (!mIsAvailable) {
                            Log.i(TAG, "MmTel enabled on slotId: " + slotId);
                            mIsAvailable = true;
                        }
                        break;
                    }
                    case ImsFeature.FEATURE_EMERGENCY_MMTEL: {
                        mSupportsEmergencyCalling = true;
                        Log.i(TAG, "Emergency calling enabled on slotId: " + slotId);
                        break;
                    }
                }

            }
        }

        @Override
        public void imsFeatureRemoved(int slotId, int feature) throws RemoteException {
            synchronized (mLock) {
                if(mSlotId != slotId) {
                    return;
                }
                switch (feature) {
                    case ImsFeature.FEATURE_MMTEL: {
                        Log.i(TAG, "MmTel removed on slotId: " + slotId);
                        onRemovedOrDied();
                        break;
                    }
                    case ImsFeature.FEATURE_EMERGENCY_MMTEL : {
                        mSupportsEmergencyCalling = false;
                        Log.i(TAG, "Emergency calling disabled on slotId: " + slotId);
                        break;
                    }
                }
            }
        }

        @Override
        public void imsStatusChanged(int slotId, int feature, int status) throws RemoteException {
            synchronized (mLock) {
                Log.i(TAG, "imsStatusChanged: slot: " + slotId + " feature: " + feature +
                        " status: " + status);
                if (mSlotId == slotId && feature == ImsFeature.FEATURE_MMTEL) {
                    mFeatureStateCached = status;
                    if (mStatusCallback != null) {
                        mStatusCallback.notifyStateChanged();
                    }
                }
            }
        }
    };

    public MmTelFeatureConnection(Context context, int slotId) {
        mSlotId = slotId;
        mContext = context;
    }

    /**
     * Called when the MmTelFeature has either been removed by Telephony or crashed.
     */
    private void onRemovedOrDied() {
        synchronized (mLock) {
            mRegistrationCallbackManager.close();
            mCapabilityCallbackManager.close();
            if (mIsAvailable) {
                mIsAvailable = false;
                // invalidate caches.
                mRegistrationBinder = null;
                mConfigBinder = null;
                if (mBinder != null) {
                    mBinder.unlinkToDeath(mDeathRecipient, 0);
                }
                if (mStatusCallback != null) {
                    mStatusCallback.notifyUnavailable();
                }
            }
        }
    }

    private @Nullable IImsRegistration getRegistration() {
        synchronized (mLock) {
            // null if cache is invalid;
            if (mRegistrationBinder != null) {
                return mRegistrationBinder;
            }
        }
        TelephonyManager tm = getTelephonyManager(mContext);
        // We don't want to synchronize on a binder call to another process.
        IImsRegistration regBinder = tm != null
                ? tm.getImsRegistration(mSlotId, ImsFeature.FEATURE_MMTEL) : null;
        synchronized (mLock) {
            // mRegistrationBinder may have changed while we tried to get the registration
            // interface.
            if (mRegistrationBinder == null) {
                mRegistrationBinder = regBinder;
            }
        }
        return mRegistrationBinder;
    }

    private IImsConfig getConfig() {
        synchronized (mLock) {
            // null if cache is invalid;
            if (mConfigBinder != null) {
                return mConfigBinder;
            }
        }
        TelephonyManager tm = getTelephonyManager(mContext);
        IImsConfig configBinder = tm != null
                ? tm.getImsConfig(mSlotId, ImsFeature.FEATURE_MMTEL) : null;
        synchronized (mLock) {
            // mConfigBinder may have changed while we tried to get the config interface.
            if (mConfigBinder == null) {
                mConfigBinder = configBinder;
            }
        }
        return mConfigBinder;
    }

    public boolean isEmergencyMmTelAvailable() {
        return mSupportsEmergencyCalling;
    }

    public IImsServiceFeatureCallback getListener() {
        return mListenerBinder;
    }

    public void setBinder(IBinder binder) {
        synchronized (mLock) {
            mBinder = binder;
            try {
                if (mBinder != null) {
                    mBinder.linkToDeath(mDeathRecipient, 0);
                }
            } catch (RemoteException e) {
                // No need to do anything if the binder is already dead.
            }
        }
    }

    /**
     * Opens the connection to the {@link MmTelFeature} and establishes a listener back to the
     * framework. Calling this method multiple times will reset the listener attached to the
     * {@link MmTelFeature}.
     * @param listener A {@link MmTelFeature.Listener} that will be used by the {@link MmTelFeature}
     * to notify the framework of updates.
     */
    public void openConnection(MmTelFeature.Listener listener) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).setListener(listener);
        }
    }

    public void closeConnection() {
        mRegistrationCallbackManager.close();
        mCapabilityCallbackManager.close();
        try {
            synchronized (mLock) {
                if (isBinderAlive()) {
                    getServiceInterface(mBinder).setListener(null);
                }
            }
        } catch (RemoteException e) {
            Log.w(TAG, "closeConnection: couldn't remove listener!");
        }
    }

    public void addRegistrationCallback(ImsRegistrationImplBase.Callback callback)
            throws RemoteException {
        mRegistrationCallbackManager.addCallback(callback);
    }

    public void removeRegistrationCallback(ImsRegistrationImplBase.Callback callback)
            throws RemoteException {
        mRegistrationCallbackManager.removeCallback(callback);
    }

    public void addCapabilityCallback(ImsFeature.CapabilityCallback callback)
            throws RemoteException {
        mCapabilityCallbackManager.addCallback(callback);
    }

    public void removeCapabilityCallback(ImsFeature.CapabilityCallback callback)
            throws RemoteException {
        mCapabilityCallbackManager.removeCallback(callback);
    }

    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            ImsFeature.CapabilityCallback callback) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).changeCapabilitiesConfiguration(request, callback);
        }
    }

    public void queryEnabledCapabilities(int capability, int radioTech,
            ImsFeature.CapabilityCallback callback) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).queryCapabilityConfiguration(capability, radioTech,
                    callback);
        }
    }

    public MmTelFeature.MmTelCapabilities queryCapabilityStatus() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return new MmTelFeature.MmTelCapabilities(
                    getServiceInterface(mBinder).queryCapabilityStatus());
        }
    }

    public ImsCallProfile createCallProfile(int callServiceType, int callType)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).createCallProfile(callServiceType, callType);
        }
    }

    public IImsCallSession createCallSession(ImsCallProfile profile)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).createCallSession(profile);
        }
    }

    public IImsUt getUtInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getUtInterface();
        }
    }

    public IImsConfig getConfigInterface() throws RemoteException {
        return getConfig();
    }

    public @ImsRegistrationImplBase.ImsRegistrationTech int getRegistrationTech()
            throws RemoteException {
        IImsRegistration registration = getRegistration();
        if (registration != null) {
                return registration.getRegistrationTechnology();
        } else {
            return ImsRegistrationImplBase.REGISTRATION_TECH_NONE;
        }
    }

    public IImsEcbm getEcbmInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getEcbmInterface();
        }
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).setUiTtyMode(uiTtyMode, onComplete);
        }
    }

    public IImsMultiEndpoint getMultiEndpointInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getMultiEndpointInterface();
        }
    }

    public void sendSms(int token, int messageRef, String format, String smsc, boolean isRetry,
            byte[] pdu) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).sendSms(token, messageRef, format, smsc, isRetry,
                    pdu);
        }
    }

    public void acknowledgeSms(int token, int messageRef,
            @ImsSmsImplBase.SendStatusResult int result) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).acknowledgeSms(token, messageRef, result);
        }
    }

    public void acknowledgeSmsReport(int token, int messageRef,
            @ImsSmsImplBase.StatusReportResult int result) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).acknowledgeSmsReport(token, messageRef, result);
        }
    }

    public String getSmsFormat() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getSmsFormat();
        }
    }

    public void onSmsReady() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).onSmsReady();
        }
    }

    public void setSmsListener(IImsSmsListener listener) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).setSmsListener(listener);
        }
    }

    public @MmTelFeature.ProcessCallResult int shouldProcessCall(boolean isEmergency,
            String[] numbers) throws RemoteException {
        if (isEmergency && !isEmergencyMmTelAvailable()) {
            // Don't query the ImsService if emergency calling is not available on the ImsService.
            Log.i(TAG, "MmTel does not support emergency over IMS, fallback to CS.");
            return MmTelFeature.PROCESS_CALL_CSFB;
        }
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).shouldProcessCall(numbers);
        }
    }

    /**
     * @return an integer describing the current Feature Status, defined in
     * {@link ImsFeature.ImsState}.
     */
    public int getFeatureState() {
        synchronized (mLock) {
            if (isBinderAlive() && mFeatureStateCached != null) {
                return mFeatureStateCached;
            }
        }
        // Don't synchronize on Binder call.
        Integer status = retrieveFeatureState();
        synchronized (mLock) {
            if (status == null) {
                return ImsFeature.STATE_UNAVAILABLE;
            }
            // Cache only non-null value for feature status.
            mFeatureStateCached = status;
        }
        Log.i(TAG, "getFeatureState - returning " + status);
        return status;
    }

    /**
     * Internal method used to retrieve the feature status from the corresponding ImsService.
     */
    private Integer retrieveFeatureState() {
        if (mBinder != null) {
            try {
                return getServiceInterface(mBinder).getFeatureState();
            } catch (RemoteException e) {
                // Status check failed, don't update cache
            }
        }
        return null;
    }

    /**
     * @param c Callback that will fire when the feature status has changed.
     */
    public void setStatusCallback(IFeatureUpdate c) {
        mStatusCallback = c;
    }

    /**
     * @return Returns true if the ImsService is ready to take commands, false otherwise. If this
     * method returns false, it doesn't mean that the Binder connection is not available (use
     * {@link #isBinderReady()} to check that), but that the ImsService is not accepting commands
     * at this time.
     *
     * For example, for DSDS devices, only one slot can be {@link ImsFeature#STATE_READY} to take
     * commands at a time, so the other slot must stay at {@link ImsFeature#STATE_UNAVAILABLE}.
     */
    public boolean isBinderReady() {
        return isBinderAlive() && getFeatureState() == ImsFeature.STATE_READY;
    }

    /**
     * @return false if the binder connection is no longer alive.
     */
    public boolean isBinderAlive() {
        return mIsAvailable && mBinder != null && mBinder.isBinderAlive();
    }

    protected void checkServiceIsReady() throws RemoteException {
        if (!isBinderReady()) {
            throw new RemoteException("ImsServiceProxy is not ready to accept commands.");
        }
    }

    private IImsMmTelFeature getServiceInterface(IBinder b) {
        return IImsMmTelFeature.Stub.asInterface(b);
    }

    protected void checkBinderConnection() throws RemoteException {
        if (!isBinderAlive()) {
            throw new RemoteException("ImsServiceProxy is not available for that feature.");
        }
    }
}
