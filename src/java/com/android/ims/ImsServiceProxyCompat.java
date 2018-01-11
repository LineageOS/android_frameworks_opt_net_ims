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

import android.app.PendingIntent;
import android.content.Context;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.ims.feature.ImsFeature;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMMTelFeature;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsService;
import com.android.ims.internal.IImsUt;

/**
 * Compatibility class that implements the new ImsService MMTelFeature interface, but
 * uses the old IImsService interface to support older devices that implement the deprecated
 * opt/net/ims interface.
 * @hide
 */

public class ImsServiceProxyCompat extends ImsServiceProxy {

    private static final int SERVICE_ID = ImsFeature.MMTEL;

    /**
     * For accessing the IMS related service.
     * Internal use only.
     * @hide
     */
    private static final String IMS_SERVICE = "ims";

    public static ImsServiceProxyCompat create(Context context, int slotId,
            IBinder.DeathRecipient recipient) {
        IBinder binder = ServiceManager.checkService(IMS_SERVICE);

        if (binder != null) {
            try {
                binder.linkToDeath(recipient, 0);
            } catch (RemoteException e) {
            }
        }

        // If the proxy is created with a null binder, subsequent calls that depend on a live
        // binder will fail, causing this structure to be torn down and created again.
        return new ImsServiceProxyCompat(context, slotId, binder);
    }

    public ImsServiceProxyCompat(Context context, int slotId, IBinder binder) {
        super(context, slotId, binder, SERVICE_ID);
    }

    @Override
    public int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener)
            throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).open(mSlotId, ImsFeature.MMTEL, incomingCallIntent,
                listener);
    }

    @Override
    public void endSession(int sessionId) throws RemoteException {
        checkBinderConnection();
        getServiceInterface(mBinder).close(sessionId);
    }

    @Override
    public boolean isConnected(int callServiceType, int callType)
            throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).isConnected(SERVICE_ID,  callServiceType, callType);
    }

    @Override
    public boolean isOpened() throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).isOpened(SERVICE_ID);
    }

    @Override
    public void addRegistrationListener(IImsRegistrationListener listener)
            throws RemoteException {
        checkBinderConnection();
        getServiceInterface(mBinder).addRegistrationListener(mSlotId, ImsFeature.MMTEL, listener);
    }

    @Override
    public void removeRegistrationListener(IImsRegistrationListener listener)
            throws RemoteException {
        // Not Implemented in old ImsService. If the registration listener becomes invalid, the
        // ImsService will remove.
    }

    @Override
    public ImsCallProfile createCallProfile(int sessionId, int callServiceType, int callType)
            throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).createCallProfile(sessionId, callServiceType, callType);
    }

    @Override
    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile)
            throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).createCallSession(sessionId, profile, null);
    }

    @Override
    public IImsCallSession getPendingCallSession(int sessionId, String callId)
            throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).getPendingCallSession(sessionId, callId);
    }

    @Override
    public IImsUt getUtInterface() throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).getUtInterface(SERVICE_ID);
    }

    @Override
    public IImsConfig getConfigInterface() throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).getConfigInterface(mSlotId);
    }

    @Override
    public void turnOnIms() throws RemoteException {
        checkBinderConnection();
        getServiceInterface(mBinder).turnOnIms(mSlotId);
    }

    @Override
    public void turnOffIms() throws RemoteException {
        checkBinderConnection();
        getServiceInterface(mBinder).turnOffIms(mSlotId);
    }

    @Override
    public IImsEcbm getEcbmInterface() throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).getEcbmInterface(SERVICE_ID);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete)
            throws RemoteException {
        checkBinderConnection();
        getServiceInterface(mBinder).setUiTTYMode(SERVICE_ID, uiTtyMode, onComplete);
    }

    @Override
    public IImsMultiEndpoint getMultiEndpointInterface() throws RemoteException {
        checkBinderConnection();
        return getServiceInterface(mBinder).getMultiEndpointInterface(SERVICE_ID);
    }
    @Override
    public int getFeatureStatus() {
        return ImsFeature.STATE_READY;
    }

    @Override
    public boolean isBinderAlive() {
        return mBinder != null && mBinder.isBinderAlive();
    }

    private IImsService getServiceInterface(IBinder b) {
        return IImsService.Stub.asInterface(b);
    }
}
