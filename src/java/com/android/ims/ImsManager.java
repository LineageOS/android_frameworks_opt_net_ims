/*
 * Copyright (c) 2013 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.ims;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationCallback;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsServiceController;
import com.android.ims.internal.IImsSmsListener;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.ImsCallSession;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Provides APIs for IMS services, such as initiating IMS calls, and provides access to
 * the operator's IMS network. This class is the starting point for any IMS actions.
 * You can acquire an instance of it with {@link #getInstance getInstance()}.</p>
 * <p>The APIs in this class allows you to:</p>
 *
 * @hide
 */
public class ImsManager {

    /*
     * Debug flag to override configuration flag
     */
    public static final String PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE = "persist.dbg.volte_avail_ovr";
    public static final int PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE_DEFAULT = 0;
    public static final String PROPERTY_DBG_VT_AVAIL_OVERRIDE = "persist.dbg.vt_avail_ovr";
    public static final int PROPERTY_DBG_VT_AVAIL_OVERRIDE_DEFAULT = 0;
    public static final String PROPERTY_DBG_WFC_AVAIL_OVERRIDE = "persist.dbg.wfc_avail_ovr";
    public static final int PROPERTY_DBG_WFC_AVAIL_OVERRIDE_DEFAULT = 0;
    public static final String PROPERTY_DBG_ALLOW_IMS_OFF_OVERRIDE = "persist.dbg.allow_ims_off";
    public static final int PROPERTY_DBG_ALLOW_IMS_OFF_OVERRIDE_DEFAULT = 0;

    /**
     * The result code to be sent back with the incoming call {@link PendingIntent}.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     */
    public static final int INCOMING_CALL_RESULT_CODE = 101;

    /**
     * Key to retrieve the call ID from an incoming call intent.
     * @see #open(PendingIntent, ImsConnectionStateListener)
     */
    public static final String EXTRA_CALL_ID = "android:imsCallID";

    /**
     * Action to broadcast when ImsService is up.
     * Internal use only.
     * @deprecated
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_UP =
            "com.android.ims.IMS_SERVICE_UP";

    /**
     * Action to broadcast when ImsService is down.
     * Internal use only.
     * @deprecated
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_DOWN =
            "com.android.ims.IMS_SERVICE_DOWN";

    /**
     * Action to broadcast when ImsService registration fails.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_REGISTRATION_ERROR =
            "com.android.ims.REGISTRATION_ERROR";

    /**
     * Part of the ACTION_IMS_SERVICE_UP or _DOWN intents.
     * A long value; the phone ID corresponding to the IMS service coming up or down.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_PHONE_ID = "android:phone_id";

    /**
     * Action for the incoming call intent for the Phone app.
     * Internal use only.
     * @hide
     */
    public static final String ACTION_IMS_INCOMING_CALL =
            "com.android.ims.IMS_INCOMING_CALL";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * An integer value; service identifier obtained from {@link ImsManager#open}.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_SERVICE_ID = "android:imsServiceId";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * An boolean value; Flag to indicate that the incoming call is a normal call or call for USSD.
     * The value "true" indicates that the incoming call is for USSD.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_USSD = "android:ussd";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * A boolean value; Flag to indicate whether the call is an unknown
     * dialing call. Such calls are originated by sending commands (like
     * AT commands) directly to modem without Android involvement.
     * Even though they are not incoming calls, they are propagated
     * to Phone app using same ACTION_IMS_INCOMING_CALL intent.
     * Internal use only.
     * @hide
     */
    public static final String EXTRA_IS_UNKNOWN_CALL = "android:isUnknown";

    private static final int SYSTEM_PROPERTY_NOT_SET = -1;

    // -1 indicates a subscriptionProperty value that is never set.
    private static final int SUB_PROPERTY_NOT_INITIALIZED = -1;

    private static final String TAG = "ImsManager";
    private static final boolean DBG = true;

    private static HashMap<Integer, ImsManager> sImsManagerInstances =
            new HashMap<Integer, ImsManager>();

    private Context mContext;
    private CarrierConfigManager mConfigManager;
    private int mPhoneId;
    private final boolean mConfigDynamicBind;
    private ImsServiceProxy mImsServiceProxy = null;
    private ImsServiceDeathRecipient mDeathRecipient = new ImsServiceDeathRecipient();
    // Ut interface for the supplementary service configuration
    private ImsUt mUt = null;
    // Interface to get/set ims config items
    private ImsConfig mConfig = null;
    private boolean mConfigUpdated = false;

    private ImsConfigListener mImsConfigListener;

    // ECBM interface
    private ImsEcbm mEcbm = null;

    private ImsMultiEndpoint mMultiEndpoint = null;

    private Set<ImsServiceProxy.IFeatureUpdate> mStatusCallbacks = new CopyOnWriteArraySet<>();

    // Keep track of the ImsRegistrationListenerProxys that have been created so that we can
    // remove them from the ImsService.
    private final Set<ImsConnectionStateListener> mRegistrationListeners = new HashSet<>();


    // Used for compatibility with the old Registration method
    // TODO: Remove once the compat layer is in place
    private final ImsRegistrationListenerProxy mImsRegistrationListenerProxy =
            new ImsRegistrationListenerProxy();
    // New API for registration to the ImsService.
    private final ImsRegistrationCallback mRegistrationCallback = new ImsRegistrationCallback();


    // When true, we have registered the mRegistrationListenerProxy with the ImsService. Don't do
    // it again.
    private boolean mHasRegisteredForProxy = false;
    private final Object mHasRegisteredLock = new Object();

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    // mRecentDisconnectReasons stores the last 16 disconnect reasons
    private static final int MAX_RECENT_DISCONNECT_REASONS = 16;
    private ConcurrentLinkedDeque<ImsReasonInfo> mRecentDisconnectReasons =
            new ConcurrentLinkedDeque<>();

    /**
     * Gets a manager instance.
     *
     * @param context application context for creating the manager object
     * @param phoneId the phone ID for the IMS Service
     * @return the manager instance corresponding to the phoneId
     */
    public static ImsManager getInstance(Context context, int phoneId) {
        synchronized (sImsManagerInstances) {
            if (sImsManagerInstances.containsKey(phoneId)) {
                ImsManager m = sImsManagerInstances.get(phoneId);
                // May be null for some tests
                if (m != null) {
                    m.connectIfServiceIsAvailable();
                }
                return m;
            }

            ImsManager mgr = new ImsManager(context, phoneId);
            sImsManagerInstances.put(phoneId, mgr);

            return mgr;
        }
    }

    /**
     * Returns the user configuration of Enhanced 4G LTE Mode setting.
     *
     * @deprecated Doesn't support MSIM devices. Use
     * {@link #isEnhanced4gLteModeSettingEnabledByUser()} instead.
     */
    public static boolean isEnhanced4gLteModeSettingEnabledByUser(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isEnhanced4gLteModeSettingEnabledByUser();
        }
        loge("isEnhanced4gLteModeSettingEnabledByUser: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns the user configuration of Enhanced 4G LTE Mode setting for slot. If the option is
     * not editable ({@link CarrierConfigManager#KEY_EDITABLE_ENHANCED_4G_LTE_BOOL} is false), or
     * the setting is not initialized, this method will return default value specified by
     * {@link CarrierConfigManager#KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL}.
     *
     * Note that even if the setting was set, it may no longer be editable. If this is the case we
     * return the default value.
     */
    public boolean isEnhanced4gLteModeSettingEnabledByUser() {
        int setting = SubscriptionManager.getIntegerSubscriptionProperty(
                getSubId(), SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                SUB_PROPERTY_NOT_INITIALIZED, mContext);
        boolean onByDefault = getBooleanCarrierConfig(
                CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL);

        // If Enhanced 4G LTE Mode is uneditable or not initialized, we use the default value
        if (!getBooleanCarrierConfig(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL)
                || setting == SUB_PROPERTY_NOT_INITIALIZED) {
            return onByDefault;
        } else {
            return (setting == ImsConfig.FeatureValueConstants.ON);
        }
    }

    /**
     * Change persistent Enhanced 4G LTE Mode setting.
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #setEnhanced4gLteModeSetting(boolean)}
     * instead.
     */
    public static void setEnhanced4gLteModeSetting(Context context, boolean enabled) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setEnhanced4gLteModeSetting(enabled);
        }
        loge("setEnhanced4gLteModeSetting: ImsManager null, value not set.");
    }

    /**
     * Change persistent Enhanced 4G LTE Mode setting. If the option is not editable
     * ({@link CarrierConfigManager#KEY_EDITABLE_ENHANCED_4G_LTE_BOOL} is false), this method will
     * set the setting to the default value specified by
     * {@link CarrierConfigManager#KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL}.
     *
     */
    public void setEnhanced4gLteModeSetting(boolean enabled) {
        // If editable=false, we must keep default advanced 4G mode.
        if (!getBooleanCarrierConfig(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL)) {
            enabled = getBooleanCarrierConfig(
                    CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL);
        }

        int prevSetting = SubscriptionManager.getIntegerSubscriptionProperty(
                getSubId(), SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                SUB_PROPERTY_NOT_INITIALIZED, mContext);

        if (prevSetting != (enabled ?
                   ImsConfig.FeatureValueConstants.ON :
                   ImsConfig.FeatureValueConstants.OFF)) {
            SubscriptionManager.setSubscriptionProperty(getSubId(),
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED, booleanToPropertyString(enabled));
            if (isNonTtyOrTtyOnVolteEnabled()) {
                try {
                    setAdvanced4GMode(enabled);
                } catch (ImsException ie) {
                    // do nothing
                }
            }
        }
    }

    /**
     * Indicates whether the call is non-TTY or if TTY - whether TTY on VoLTE is
     * supported.
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isNonTtyOrTtyOnVolteEnabled()} instead.
     */
    public static boolean isNonTtyOrTtyOnVolteEnabled(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isNonTtyOrTtyOnVolteEnabled();
        }
        loge("isNonTtyOrTtyOnVolteEnabled: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Indicates whether the call is non-TTY or if TTY - whether TTY on VoLTE is
     * supported on a per slot basis.
     */
    public boolean isNonTtyOrTtyOnVolteEnabled() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL)) {
            return true;
        }

        TelecomManager tm = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (tm == null) {
            Log.w(TAG, "isNonTtyOrTtyOnVolteEnabled: telecom not available");
            return true;
        }
        return tm.getCurrentTtyMode() == TelecomManager.TTY_MODE_OFF;
    }

    /**
     * Returns a platform configuration for VoLTE which may override the user setting.
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isVolteEnabledByPlatform()} instead.
     */
    public static boolean isVolteEnabledByPlatform(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isVolteEnabledByPlatform();
        }
        loge("isVolteEnabledByPlatform: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns a platform configuration for VoLTE which may override the user setting on a per Slot
     * basis.
     */
    public boolean isVolteEnabledByPlatform() {
        // We first read the per slot value. If doesn't exist, we read the general value. If still
        // doesn't exist, we use the hardcoded default value.
        if (SystemProperties.getInt(
                PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE + Integer.toString(mPhoneId),
                SYSTEM_PROPERTY_NOT_SET) == 1 ||
                SystemProperties.getInt(PROPERTY_DBG_VOLTE_AVAIL_OVERRIDE,
                        SYSTEM_PROPERTY_NOT_SET) == 1) {
            return true;
        }

        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_device_volte_available)
                && getBooleanCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)
                && isGbaValid();
    }

    /**
     * Indicates whether VoLTE is provisioned on device.
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isVolteProvisionedOnDevice()} instead.
     */
    public static boolean isVolteProvisionedOnDevice(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isVolteProvisionedOnDevice();
        }
        loge("isVolteProvisionedOnDevice: ImsManager null, returning default value.");
        return true;
    }

    /**
     * Indicates whether VoLTE is provisioned on this slot.
     */
    public boolean isVolteProvisionedOnDevice() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL)) {
            return isVolteProvisioned();
        }

        return true;
    }

    /**
     * Indicates whether VoWifi is provisioned on device.
     *
     * When CarrierConfig KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL is true, and VoLTE is not
     * provisioned on device, this method returns false.
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isWfcProvisionedOnDevice()} instead.
     */
    public static boolean isWfcProvisionedOnDevice(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isWfcProvisionedOnDevice();
        }
        loge("isWfcProvisionedOnDevice: ImsManager null, returning default value.");
        return true;
    }

    /**
     * Indicates whether VoWifi is provisioned on slot.
     *
     * When CarrierConfig KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL is true, and VoLTE is not
     * provisioned on device, this method returns false.
     */
    public boolean isWfcProvisionedOnDevice() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)) {
            if (!isVolteProvisionedOnDevice()) {
                return false;
            }
        }

        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL)) {
            return isWfcProvisioned();
        }

        return true;
    }

    /**
     * Indicates whether VT is provisioned on device
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isVtProvisionedOnDevice()} instead.
     */
    public static boolean isVtProvisionedOnDevice(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isVtProvisionedOnDevice();
        }
        loge("isVtProvisionedOnDevice: ImsManager null, returning default value.");
        return true;
    }

    /**
     * Indicates whether VT is provisioned on slot.
     */
    public boolean isVtProvisionedOnDevice() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL)) {
            return isVtProvisioned();
        }

        return true;
    }

    /**
     * Returns a platform configuration for VT which may override the user setting.
     *
     * Note: VT presumes that VoLTE is enabled (these are configuration settings
     * which must be done correctly).
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isVtEnabledByPlatform()} instead.
     */
    public static boolean isVtEnabledByPlatform(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isVtEnabledByPlatform();
        }
        loge("isVtEnabledByPlatform: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns a platform configuration for VT which may override the user setting.
     *
     * Note: VT presumes that VoLTE is enabled (these are configuration settings
     * which must be done correctly).
     */
    public boolean isVtEnabledByPlatform() {
        // We first read the per slot value. If doesn't exist, we read the general value. If still
        // doesn't exist, we use the hardcoded default value.
        if (SystemProperties.getInt(PROPERTY_DBG_VT_AVAIL_OVERRIDE +
                Integer.toString(mPhoneId), SYSTEM_PROPERTY_NOT_SET) == 1  ||
                SystemProperties.getInt(
                        PROPERTY_DBG_VT_AVAIL_OVERRIDE, SYSTEM_PROPERTY_NOT_SET) == 1) {
            return true;
        }

        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_device_vt_available) &&
                getBooleanCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL) &&
                isGbaValid();
    }

    /**
     * Returns the user configuration of VT setting
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isVtEnabledByUser()} instead.
     */
    public static boolean isVtEnabledByUser(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isVtEnabledByUser();
        }
        loge("isVtEnabledByUser: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns the user configuration of VT setting per slot. If not set, it
     * returns true as default value.
     */
    public boolean isVtEnabledByUser() {
        int setting = SubscriptionManager.getIntegerSubscriptionProperty(
                getSubId(), SubscriptionManager.VT_IMS_ENABLED,
                SUB_PROPERTY_NOT_INITIALIZED, mContext);

        // If it's never set, by default we return true.
        return (setting == SUB_PROPERTY_NOT_INITIALIZED
                || setting == ImsConfig.FeatureValueConstants.ON);
    }

    /**
     * Change persistent VT enabled setting
     *
     * @deprecated Does not support MSIM devices. Please use {@link #setVtSetting(boolean)} instead.
     */
    public static void setVtSetting(Context context, boolean enabled) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setVtSetting(enabled);
        }
        loge("setVtSetting: ImsManager null, can not set value.");
    }

    /**
     * Change persistent VT enabled setting for slot.
     */
    public void setVtSetting(boolean enabled) {
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.VT_IMS_ENABLED,
                booleanToPropertyString(enabled));
        try {
            ImsConfig config = getConfigInterface();
            config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE,
                    TelephonyManager.NETWORK_TYPE_LTE,
                    enabled ? ImsConfig.FeatureValueConstants.ON
                            : ImsConfig.FeatureValueConstants.OFF,
                    mImsConfigListener);

            if (enabled) {
                log("setVtSetting(b) : turnOnIms");
                turnOnIms();
            } else if (isVolteEnabledByPlatform()
                    && (!isVolteEnabledByPlatform()
                    || !isEnhanced4gLteModeSettingEnabledByUser())) {
                log("setVtSetting(b) : imsServiceAllowTurnOff -> turnOffIms");
                turnOffIms();
            }
        } catch (ImsException e) {
            loge("setVtSetting(b): ", e);
        }
    }

    /**
     * Returns whether turning off ims is allowed by platform.
     * The platform property may override the carrier config.
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isTurnOffImsAllowedByPlatform()} instead.
     */
    private static boolean isTurnOffImsAllowedByPlatform(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isTurnOffImsAllowedByPlatform();
        }
        loge("isTurnOffImsAllowedByPlatform: ImsManager null, returning default value.");
        return true;
    }

    /**
     * Returns whether turning off ims is allowed by platform.
     * The platform property may override the carrier config.
     */
    private boolean isTurnOffImsAllowedByPlatform() {
        // We first read the per slot value. If doesn't exist, we read the general value. If still
        // doesn't exist, we use the hardcoded default value.
        if (SystemProperties.getInt(PROPERTY_DBG_ALLOW_IMS_OFF_OVERRIDE +
                Integer.toString(mPhoneId), SYSTEM_PROPERTY_NOT_SET) == 1  ||
                SystemProperties.getInt(
                        PROPERTY_DBG_ALLOW_IMS_OFF_OVERRIDE, SYSTEM_PROPERTY_NOT_SET) == 1) {
            return true;
        }

        return getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_ALLOW_TURNOFF_IMS_BOOL);
    }

    /**
     * Returns the user configuration of WFC setting
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isWfcEnabledByUser()} instead.
     */
    public static boolean isWfcEnabledByUser(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isWfcEnabledByUser();
        }
        loge("isWfcEnabledByUser: ImsManager null, returning default value.");
        return true;
    }

    /**
     * Returns the user configuration of WFC setting for slot. If not set, it
     * queries CarrierConfig value as default.
     */
    public boolean isWfcEnabledByUser() {
        int setting = SubscriptionManager.getIntegerSubscriptionProperty(
                getSubId(), SubscriptionManager.WFC_IMS_ENABLED,
                SUB_PROPERTY_NOT_INITIALIZED, mContext);

        // SUB_PROPERTY_NOT_INITIALIZED indicates it's never set in sub db.
        if (setting == SUB_PROPERTY_NOT_INITIALIZED) {
            return getBooleanCarrierConfig(
                    CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL);
        } else {
            return setting == ImsConfig.FeatureValueConstants.ON;
        }
    }

    /**
     * Change persistent WFC enabled setting.
     * @deprecated Does not support MSIM devices. Please use
     * {@link #setWfcSetting} instead.
     */
    public static void setWfcSetting(Context context, boolean enabled) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setWfcSetting(enabled);
        }
        loge("setWfcSetting: ImsManager null, can not set value.");
    }

    /**
     * Change persistent WFC enabled setting for slot.
     */
    public void setWfcSetting(boolean enabled) {
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_ENABLED, booleanToPropertyString(enabled));

        setWfcNonPersistent(enabled, getWfcMode());
    }

    /**
     * Non-persistently change WFC enabled setting and WFC mode for slot
     *
     * @param wfcMode The WFC preference if WFC is enabled
     */
    public void setWfcNonPersistent(boolean enabled, int wfcMode) {
        int imsFeatureValue =
                enabled ? ImsConfig.FeatureValueConstants.ON : ImsConfig.FeatureValueConstants.OFF;
        // Force IMS to register over LTE when turning off WFC
        int imsWfcModeFeatureValue =
                enabled ? wfcMode : ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED;

        try {
            ImsConfig config = getConfigInterface();
            config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI,
                    TelephonyManager.NETWORK_TYPE_IWLAN,
                    imsFeatureValue,
                    mImsConfigListener);

            if (enabled) {
                log("setWfcSetting() : turnOnIms");
                turnOnIms();
            } else if (isTurnOffImsAllowedByPlatform()
                    && (!isVolteEnabledByPlatform()
                    || !isEnhanced4gLteModeSettingEnabledByUser())) {
                log("setWfcSetting() : imsServiceAllowTurnOff -> turnOffIms");
                turnOffIms();
            }

            setWfcModeInternal(imsWfcModeFeatureValue);
        } catch (ImsException e) {
            loge("setWfcSetting(): ", e);
        }
    }

    /**
     * Returns the user configuration of WFC preference setting.
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #getWfcMode(boolean roaming)} instead.
     */
    public static int getWfcMode(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.getWfcMode();
        }
        loge("getWfcMode: ImsManager null, returning default value.");
        return ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY;
    }

    /**
     * Returns the user configuration of WFC preference setting
     * @deprecated. Use {@link #getWfcMode(boolean roaming)} instead.
     */
    public int getWfcMode() {
        return getWfcMode(false);
    }

    /**
     * Change persistent WFC preference setting.
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #setWfcMode(int)} instead.
     */
    public static void setWfcMode(Context context, int wfcMode) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setWfcMode(wfcMode);
        }
        loge("setWfcMode: ImsManager null, can not set value.");
    }

    /**
     * Change persistent WFC preference setting for slot.
     */
    public void setWfcMode(int wfcMode) {
        if (DBG) log("setWfcMode(i) - setting=" + wfcMode);

        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_MODE, Integer.toString(wfcMode));

        setWfcModeInternal(wfcMode);
    }

    /**
     * Changes the WFC mode to its default value for Carriers with non-editable WFC settings.
     */
    private void updateDefaultWfcMode() {
        if (DBG) log("updateDefaultWfcMode");
        if (!getBooleanCarrierConfig(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL)) {
            setWfcMode(getIntCarrierConfig(
                    CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT));
        }
    }

    /**
     * Returns the user configuration of WFC preference setting
     *
     * @param roaming {@code false} for home network setting, {@code true} for roaming  setting
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #getWfcMode(boolean)} instead.
     */
    public static int getWfcMode(Context context, boolean roaming) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.getWfcMode(roaming);
        }
        loge("getWfcMode: ImsManager null, returning default value.");
        return ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY;
    }

    /**
     * Returns the user configuration of WFC preference setting for slot. If not set, it
     * queries CarrierConfig value as default.
     *
     * @param roaming {@code false} for home network setting, {@code true} for roaming  setting
     */
    public int getWfcMode(boolean roaming) {
        int setting = 0;
        if (!roaming) {
            setting = SubscriptionManager.getIntegerSubscriptionProperty(
                    getSubId(), SubscriptionManager.WFC_IMS_MODE,
                    SUB_PROPERTY_NOT_INITIALIZED, mContext);

            // SUB_PROPERTY_NOT_INITIALIZED indicates it's never set in sub db.
            if (setting == SUB_PROPERTY_NOT_INITIALIZED) {
                setting = getIntCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT);
            }
            if (DBG) log("getWfcMode - setting=" + setting);
        } else {
            setting = SubscriptionManager.getIntegerSubscriptionProperty(
                    getSubId(), SubscriptionManager.WFC_IMS_ROAMING_MODE,
                    SUB_PROPERTY_NOT_INITIALIZED, mContext);

            // SUB_PROPERTY_NOT_INITIALIZED indicates it's never set in sub db.
            if (setting == SUB_PROPERTY_NOT_INITIALIZED) {
                setting = getIntCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT);
            }

            if (DBG) log("getWfcMode (roaming) - setting=" + setting);
        }
        return setting;
    }

    /**
     * Change persistent WFC preference setting
     *
     * @param roaming {@code false} for home network setting, {@code true} for roaming setting
     *
     * @deprecated Doesn't support MSIM devices. Please use {@link #setWfcMode(int, boolean)}
     * instead.
     */
    public static void setWfcMode(Context context, int wfcMode, boolean roaming) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setWfcMode(wfcMode, roaming);
        }
        loge("setWfcMode: ImsManager null, can not set value.");
    }

    /**
     * Change persistent WFC preference setting
     *
     * @param roaming {@code false} for home network setting, {@code true} for roaming setting
     */
    public void setWfcMode(int wfcMode, boolean roaming) {
        if (!roaming) {
            if (DBG) log("setWfcMode(i,b) - setting=" + wfcMode);
            SubscriptionManager.setSubscriptionProperty(getSubId(),
                    SubscriptionManager.WFC_IMS_MODE, Integer.toString(wfcMode));
        } else {
            if (DBG) log("setWfcMode(i,b) (roaming) - setting=" + wfcMode);
            SubscriptionManager.setSubscriptionProperty(getSubId(),
                    SubscriptionManager.WFC_IMS_ROAMING_MODE, Integer.toString(wfcMode));
        }

        TelephonyManager tm = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (roaming == tm.isNetworkRoaming(getSubId())) {
            setWfcModeInternal(wfcMode);
        }
    }

    private int getSubId() {
        int[] subIds = SubscriptionManager.getSubId(mPhoneId);
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (subIds != null && subIds.length >= 1) {
            subId = subIds[0];
        }
        return subId;
    }

    private static void setWfcModeInternal(Context context, int wfcMode) {
        final ImsManager imsManager = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (imsManager != null) {
            final int value = wfcMode;
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        imsManager.getConfigInterface().setProvisionedValue(
                                ImsConfig.ConfigConstants.VOICE_OVER_WIFI_MODE,
                                value);
                    } catch (ImsException e) {
                        // do nothing
                    }
                }
            });
            thread.start();
        }
    }

    private void setWfcModeInternal(int wfcMode) {
        final int value = wfcMode;
        Thread thread = new Thread(() -> {
            try {
                getConfigInterface().setProvisionedValue(
                        ImsConfig.ConfigConstants.VOICE_OVER_WIFI_MODE, value);
            } catch (ImsException e) {
                // do nothing
            }
        });
        thread.start();
    }

    /**
     * Returns the user configuration of WFC roaming setting
     *
     * @deprecated Does not support MSIM devices. Please use
     * {@link #isWfcRoamingEnabledByUser()} instead.
     */
    public static boolean isWfcRoamingEnabledByUser(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isWfcRoamingEnabledByUser();
        }
        loge("isWfcRoamingEnabledByUser: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns the user configuration of WFC roaming setting for slot. If not set, it
     * queries CarrierConfig value as default.
     */
    public boolean isWfcRoamingEnabledByUser() {
        int setting =  SubscriptionManager.getIntegerSubscriptionProperty(
                getSubId(), SubscriptionManager.WFC_IMS_ROAMING_ENABLED,
                SUB_PROPERTY_NOT_INITIALIZED, mContext);
        if (setting == SUB_PROPERTY_NOT_INITIALIZED) {
            return getBooleanCarrierConfig(
                            CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL);
        } else {
            return setting == ImsConfig.FeatureValueConstants.ON;
        }
    }

    /**
     * Change persistent WFC roaming enabled setting
     */
    public static void setWfcRoamingSetting(Context context, boolean enabled) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.setWfcRoamingSetting(enabled);
        }
        loge("setWfcRoamingSetting: ImsManager null, value not set.");
    }

    /**
     * Change persistent WFC roaming enabled setting
     */
    public void setWfcRoamingSetting(boolean enabled) {
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_ROAMING_ENABLED, booleanToPropertyString(enabled)
        );

        setWfcRoamingSettingInternal(enabled);
    }

    private void setWfcRoamingSettingInternal(boolean enabled) {
        final int value = enabled
                ? ImsConfig.FeatureValueConstants.ON
                : ImsConfig.FeatureValueConstants.OFF;
        Thread thread = new Thread(() -> {
            try {
                getConfigInterface().setProvisionedValue(
                        ImsConfig.ConfigConstants.VOICE_OVER_WIFI_ROAMING, value);
            } catch (ImsException e) {
                // do nothing
            }
        });
        thread.start();
    }

    /**
     * Returns a platform configuration for WFC which may override the user
     * setting. Note: WFC presumes that VoLTE is enabled (these are
     * configuration settings which must be done correctly).
     *
     * @deprecated Doesn't work for MSIM devices. Use {@link #isWfcEnabledByPlatform()}
     * instead.
     */
    public static boolean isWfcEnabledByPlatform(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            return mgr.isWfcEnabledByPlatform();
        }
        loge("isWfcEnabledByPlatform: ImsManager null, returning default value.");
        return false;
    }

    /**
     * Returns a platform configuration for WFC which may override the user
     * setting per slot. Note: WFC presumes that VoLTE is enabled (these are
     * configuration settings which must be done correctly).
     */
    public boolean isWfcEnabledByPlatform() {
        // We first read the per slot value. If doesn't exist, we read the general value. If still
        // doesn't exist, we use the hardcoded default value.
        if (SystemProperties.getInt(PROPERTY_DBG_WFC_AVAIL_OVERRIDE +
                Integer.toString(mPhoneId), SYSTEM_PROPERTY_NOT_SET) == 1  ||
                SystemProperties.getInt(
                        PROPERTY_DBG_WFC_AVAIL_OVERRIDE, SYSTEM_PROPERTY_NOT_SET) == 1) {
            return true;
        }

        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_device_wfc_ims_available) &&
                getBooleanCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL) &&
                isGbaValid();
    }

    /**
     * If carrier requires that IMS is only available if GBA capable SIM is used,
     * then this function checks GBA bit in EF IST.
     *
     * Format of EF IST is defined in 3GPP TS 31.103 (Section 4.2.7).
     */
    private boolean isGbaValid() {
        if (getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)) {
            final TelephonyManager telephonyManager = new TelephonyManager(mContext, getSubId());
            String efIst = telephonyManager.getIsimIst();
            if (efIst == null) {
                loge("isGbaValid - ISF is NULL");
                return true;
            }
            boolean result = efIst != null && efIst.length() > 1 &&
                    (0x02 & (byte)efIst.charAt(1)) != 0;
            if (DBG) log("isGbaValid - GBA capable=" + result + ", ISF=" + efIst);
            return result;
        }
        return true;
    }

    /**
     * Will return with config value or throw an ImsException if we receive an error from
     * ImsConfig for that value.
     */
    private boolean getProvisionedBool(ImsConfig config, int item) throws ImsException {
        int value = config.getProvisionedValue(item);
        if (value == ImsConfig.OperationStatusConstants.UNKNOWN) {
            throw new ImsException("getProvisionedBool failed with error for item: " + item,
                    ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
        }
        return config.getProvisionedValue(item) == ImsConfig.FeatureValueConstants.ON;
    }

    /**
     * Will return with config value or return false if we receive an error from
     * ImsConfig for that value.
     */
    private boolean getProvisionedBoolNoException(int item) {
        try {
            ImsConfig config = getConfigInterface();
            return getProvisionedBool(config, item);
        } catch (ImsException ex) {
            return false;
        }
    }

    /**
     * Sync carrier config and user settings with ImsConfig.
     *
     * @param context for the manager object
     * @param phoneId phone id
     * @param force update
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #updateImsServiceConfig(boolean)}
     * instead.
     */
    public static void updateImsServiceConfig(Context context, int phoneId, boolean force) {
        ImsManager mgr = ImsManager.getInstance(context, phoneId);
        if (mgr != null) {
            mgr.updateImsServiceConfig(force);
        }
        loge("updateImsServiceConfig: ImsManager null, returning without update.");
    }

    /**
     * Sync carrier config and user settings with ImsConfig.
     *
     * @param force update
     */
    public void updateImsServiceConfig(boolean force) {
        if (!force) {
            TelephonyManager tm = new TelephonyManager(mContext, getSubId());
            if (tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
                log("updateImsServiceConfig: SIM not ready");
                // Don't disable IMS if SIM is not ready
                return;
            }
        }

        if (!mConfigUpdated || force) {
            try {
                // TODO: Extend ImsConfig API and set all feature values in single function call.

                // Note: currently the order of updates is set to produce different order of
                // setFeatureValue() function calls from setAdvanced4GMode(). This is done to
                // differentiate this code path from vendor code perspective.
                boolean isImsUsed = updateVolteFeatureValue();
                isImsUsed |= updateWfcFeatureAndProvisionedValues();
                isImsUsed |= updateVideoCallFeatureValue();

                if (isImsUsed || !isTurnOffImsAllowedByPlatform()) {
                    // Turn on IMS if it is used.
                    // Also, if turning off is not allowed for current carrier,
                    // we need to turn IMS on because it might be turned off before
                    // phone switched to current carrier.
                    log("updateImsServiceConfig: turnOnIms");
                    turnOnIms();
                } else {
                    // Turn off IMS if it is not used AND turning off is allowed for carrier.
                    log("updateImsServiceConfig: turnOffIms");
                    turnOffIms();
                }

                mConfigUpdated = true;
            } catch (ImsException e) {
                loge("updateImsServiceConfig: ", e);
                mConfigUpdated = false;
            }
        }
    }

    /**
     * Update VoLTE config
     * @return whether feature is On
     * @throws ImsException
     */
    private boolean updateVolteFeatureValue() throws ImsException {
        boolean available = isVolteEnabledByPlatform();
        boolean enabled = isEnhanced4gLteModeSettingEnabledByUser();
        boolean isNonTty = isNonTtyOrTtyOnVolteEnabled();
        boolean isFeatureOn = available && enabled && isNonTty;

        log("updateVolteFeatureValue: available = " + available
                + ", enabled = " + enabled
                + ", nonTTY = " + isNonTty);

        getConfigInterface().setFeatureValue(
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE,
                TelephonyManager.NETWORK_TYPE_LTE,
                isFeatureOn ?
                        ImsConfig.FeatureValueConstants.ON :
                        ImsConfig.FeatureValueConstants.OFF,
                mImsConfigListener);

        return isFeatureOn;
    }

    /**
     * Update video call over LTE config
     * @return whether feature is On
     * @throws ImsException
     */
    private boolean updateVideoCallFeatureValue() throws ImsException {
        boolean available = isVtEnabledByPlatform();
        boolean enabled = isVtEnabledByUser();
        boolean isNonTty = isNonTtyOrTtyOnVolteEnabled();
        boolean isDataEnabled = isDataEnabled();
        boolean ignoreDataEnabledChanged = getBooleanCarrierConfig(
                CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS);

        boolean isFeatureOn = available && enabled && isNonTty
                && (ignoreDataEnabledChanged || isDataEnabled);

        log("updateVideoCallFeatureValue: available = " + available
                + ", enabled = " + enabled
                + ", nonTTY = " + isNonTty
                + ", data enabled = " + isDataEnabled);

        getConfigInterface().setFeatureValue(
                ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE,
                TelephonyManager.NETWORK_TYPE_LTE,
                isFeatureOn ?
                        ImsConfig.FeatureValueConstants.ON :
                        ImsConfig.FeatureValueConstants.OFF,
                mImsConfigListener);

        return isFeatureOn;
    }

    /**
     * Update WFC config
     * @return whether feature is On
     * @throws ImsException
     */
    private boolean updateWfcFeatureAndProvisionedValues() throws ImsException {
        TelephonyManager tm = new TelephonyManager(mContext, getSubId());
        boolean isNetworkRoaming = tm.isNetworkRoaming();
        boolean available = isWfcEnabledByPlatform();
        boolean enabled = isWfcEnabledByUser();
        updateDefaultWfcMode();
        int mode = getWfcMode(isNetworkRoaming);
        boolean roaming = isWfcRoamingEnabledByUser();
        boolean isFeatureOn = available && enabled;

        log("updateWfcFeatureAndProvisionedValues: available = " + available
                + ", enabled = " + enabled
                + ", mode = " + mode
                + ", roaming = " + roaming);

        getConfigInterface().setFeatureValue(
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                isFeatureOn ?
                        ImsConfig.FeatureValueConstants.ON :
                        ImsConfig.FeatureValueConstants.OFF,
                mImsConfigListener);

        if (!isFeatureOn) {
            mode = ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED;
            roaming = false;
        }
        setWfcModeInternal(mContext, mode);
        setWfcRoamingSettingInternal(roaming);

        return isFeatureOn;
    }

    /**
     * Do NOT use this directly, instead use {@link #getInstance(Context, int)}.
     */
    @VisibleForTesting
    public ImsManager(Context context, int phoneId) {
        mContext = context;
        mPhoneId = phoneId;
        mConfigDynamicBind = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_dynamic_bind_ims);
        mConfigManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        createImsService();
    }

    /**
     * @return Whether or not ImsManager is configured to Dynamically bind or not to support legacy
     * devices.
     */
    public boolean isDynamicBinding() {
        return mConfigDynamicBind;
    }

    /*
     * Returns a flag indicating whether the IMS service is available. If it is not available,
     * it will try to connect before reporting failure.
     */
    public boolean isServiceAvailable() {
        connectIfServiceIsAvailable();
        // mImsServiceProxy will always create an ImsServiceProxy.
        return mImsServiceProxy.isBinderAlive();
    }

    /*
     * Returns a flag indicating whether the IMS service is ready to send requests to lower layers.
     */
    public boolean isServiceReady() {
        connectIfServiceIsAvailable();
        return mImsServiceProxy.isBinderReady();
    }

    /**
     * If the service is available, try to reconnect.
     */
    public void connectIfServiceIsAvailable() {
        if (mImsServiceProxy == null || !mImsServiceProxy.isBinderAlive()) {
            createImsService();
        }
    }

    public void setImsConfigListener(ImsConfigListener listener) {
        mImsConfigListener = listener;
    }


    /**
     * Adds a callback for status changed events if the binder is already available. If it is not,
     * this method will throw an ImsException.
     */
    public void addNotifyStatusChangedCallbackIfAvailable(ImsServiceProxy.IFeatureUpdate c)
            throws ImsException {
        if (!mImsServiceProxy.isBinderAlive()) {
            throw new ImsException("Binder is not active!",
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        if (c != null) {
            mStatusCallbacks.add(c);
        }
    }

    public void removeNotifyStatusChangedCallback(ImsServiceProxy.IFeatureUpdate c) {
        if (c != null) {
            mStatusCallbacks.remove(c);
        } else {
            Log.w(TAG, "removeNotifyStatusChangedCallback: callback is null!");
        }
    }

    /**
     * Opens the IMS service for making calls and/or receiving generic IMS calls.
     * The caller may make subsquent calls through {@link #makeCall}.
     * The IMS service will register the device to the operator's network with the credentials
     * (from ISIM) periodically in order to receive calls from the operator's network.
     * When the IMS service receives a new call, it will send out an intent with
     * the provided action string.
     * The intent contains a call ID extra {@link getCallId} and it can be used to take a call.
     *
     * @param serviceClass a service class specified in {@link ImsServiceClass}
     *      For VoLTE service, it MUST be a {@link ImsServiceClass#MMTEL}.
     * @param incomingCallPendingIntent When an incoming call is received,
     *        the IMS service will call {@link PendingIntent#send(Context, int, Intent)} to
     *        send back the intent to the caller with {@link #INCOMING_CALL_RESULT_CODE}
     *        as the result code and the intent to fill in the call ID; It cannot be null
     * @param listener To listen to IMS registration events; It cannot be null
     * @return identifier (greater than 0) for the specified service
     * @throws NullPointerException if {@code incomingCallPendingIntent}
     *      or {@code listener} is null
     * @throws ImsException if calling the IMS service results in an error
     * @see #getCallId
     * @see #getImsSessionId
     */
    public int open(int serviceClass, PendingIntent incomingCallPendingIntent,
            ImsConnectionStateListener listener) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        if (incomingCallPendingIntent == null) {
            throw new NullPointerException("incomingCallPendingIntent can't be null");
        }

        if (listener == null) {
            throw new NullPointerException("listener can't be null");
        }

        int result = 0;

        try {
            // Register a stub implementation of the ImsRegistrationListener. There is the
            // possibility that if we use the real implementation of the ImsRegistrationListener,
            // it will be added twice.
            // TODO: Remove ImsRegistrationListener from startSession API (b/62588776)
            result = mImsServiceProxy.startSession(incomingCallPendingIntent,
                    new ImsRegistrationListenerBase());
            addRegistrationListener(listener);
            log("open: Session started and registration listener added.");
        } catch (RemoteException e) {
            throw new ImsException("open()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }

        if (result <= 0) {
            // If the return value is a minus value,
            // it means that an error occurred in the service.
            // So, it needs to convert to the reason code specified in ImsReasonInfo.
            throw new ImsException("open()", (result * (-1)));
        }

        return result;
    }

    /**
     * Adds registration listener to the IMS service.
     *
     * @param serviceClass a service class specified in {@link ImsServiceClass}
     *      For VoLTE service, it MUST be a {@link ImsServiceClass#MMTEL}.
     * @param listener To listen to IMS registration events; It cannot be null
     * @throws NullPointerException if {@code listener} is null
     * @throws ImsException if calling the IMS service results in an error
     *
     * @deprecated Use {@link #addRegistrationListener(ImsConnectionStateListener)} instead.
     */
    public void addRegistrationListener(int serviceClass, ImsConnectionStateListener listener)
            throws ImsException {
        addRegistrationListener(listener);
    }

    /**
     * Adds registration listener to the IMS service.
     *
     * @param listener To listen to IMS registration events; It cannot be null
     * @throws NullPointerException if {@code listener} is null
     * @throws ImsException if calling the IMS service results in an error
     */
    public void addRegistrationListener(ImsConnectionStateListener listener) throws ImsException {
        if (listener == null) {
            throw new NullPointerException("listener can't be null");
        }
        // We only want this Proxy registered once.
        synchronized (mHasRegisteredLock) {
            if (!mHasRegisteredForProxy) {
                try {
                    checkAndThrowExceptionIfServiceUnavailable();
                    // TODO: Remove once new MmTelFeature is merged in
                    mImsServiceProxy.addRegistrationListener(mImsRegistrationListenerProxy);
                    mImsServiceProxy.getRegistration().addRegistrationCallback(
                            mRegistrationCallback);
                    log("Registration Callback/Listener registered.");
                    // Only record if there isn't a RemoteException.
                    mHasRegisteredForProxy = true;
                } catch (RemoteException e) {
                    throw new ImsException("addRegistrationListener()", e,
                            ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
                }
            }
        }
        synchronized (mRegistrationListeners) {
            log("Local registration listener added: " + listener);
            mRegistrationListeners.add(listener);
        }
    }

    /**
     * Removes the registration listener from the IMS service.
     *
     * @param listener Previously registered listener that will be removed. Can not be null.
     * @throws NullPointerException if {@code listener} is null
     * @throws ImsException if calling the IMS service results in an error
     * instead.
     */
    public void removeRegistrationListener(ImsConnectionStateListener listener)
            throws ImsException {
        if (listener == null) {
            throw new NullPointerException("listener can't be null");
        }

        synchronized (mRegistrationListeners) {
            log("Local registration listener removed: " + listener);
            mRegistrationListeners.remove(listener);
        }
    }

    /**
     * Closes the specified service ({@link ImsServiceClass}) not to make/receive calls.
     * All the resources that were allocated to the service are also released.
     *
     * @param sessionId a session id to be closed which is obtained from {@link ImsManager#open}
     * @throws ImsException if calling the IMS service results in an error
     */
    public void close(int sessionId) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsServiceProxy.endSession(sessionId);
        } catch (RemoteException e) {
            throw new ImsException("close()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        } finally {
            mUt = null;
            mConfig = null;
            mEcbm = null;
            mMultiEndpoint = null;
        }
    }

    /**
     * Gets the configuration interface to provision / withdraw the supplementary service settings.
     *
     * @return the Ut interface instance
     * @throws ImsException if getting the Ut interface results in an error
     */
    public ImsUtInterface getSupplementaryServiceConfiguration()
            throws ImsException {
        // FIXME: manage the multiple Ut interfaces based on the session id
        if (mUt != null && mUt.isBinderAlive()) {
            return mUt;
        }

        checkAndThrowExceptionIfServiceUnavailable();
        try {
            IImsUt iUt = mImsServiceProxy.getUtInterface();

            if (iUt == null) {
                throw new ImsException("getSupplementaryServiceConfiguration()",
                        ImsReasonInfo.CODE_UT_NOT_SUPPORTED);
            }

            mUt = new ImsUt(iUt);
        } catch (RemoteException e) {
            throw new ImsException("getSupplementaryServiceConfiguration()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return mUt;
    }

    /**
     * Checks if the IMS service has successfully registered to the IMS network
     * with the specified service & call type.
     *
     * @param serviceType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE_N_VIDEO}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     * @return true if the specified service id is connected to the IMS network;
     *        false otherwise
     * @throws ImsException if calling the IMS service results in an error
     */
    public boolean isConnected(int serviceType, int callType)
            throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsServiceProxy.isConnected(serviceType, callType);
        } catch (RemoteException e) {
            throw new ImsException("isServiceConnected()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Checks if the specified IMS service is opend.
     *
     * @return true if the specified service id is opened; false otherwise
     * @throws ImsException if calling the IMS service results in an error
     */
    public boolean isOpened() throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsServiceProxy.isOpened();
        } catch (RemoteException e) {
            throw new ImsException("isOpened()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Creates a {@link ImsCallProfile} from the service capabilities & IMS registration state.
     *
     * @param sessionId a session id which is obtained from {@link ImsManager#open}
     * @param serviceType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NONE}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VT_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_RX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_NODIR}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     *        {@link ImsCallProfile#CALL_TYPE_VS_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VS_RX}
     * @return a {@link ImsCallProfile} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCallProfile createCallProfile(int sessionId, int serviceType, int callType)
            throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            return mImsServiceProxy.createCallProfile(sessionId, serviceType, callType);
        } catch (RemoteException e) {
            throw new ImsException("createCallProfile()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    /**
     * Creates a {@link ImsCall} to make a call.
     *
     * @param sessionId a session id which is obtained from {@link ImsManager#open}
     * @param profile a call profile to make the call
     *      (it contains service type, call type, media information, etc.)
     * @param participants participants to invite the conference call
     * @param listener listen to the call events from {@link ImsCall}
     * @return a {@link ImsCall} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCall makeCall(int sessionId, ImsCallProfile profile, String[] callees,
            ImsCall.Listener listener) throws ImsException {
        if (DBG) {
            log("makeCall :: sessionId=" + sessionId
                    + ", profile=" + profile);
        }

        checkAndThrowExceptionIfServiceUnavailable();

        ImsCall call = new ImsCall(mContext, profile);

        call.setListener(listener);
        ImsCallSession session = createCallSession(sessionId, profile);

        if ((callees != null) && (callees.length == 1)) {
            call.start(session, callees[0]);
        } else {
            call.start(session, callees);
        }

        return call;
    }

    /**
     * Creates a {@link ImsCall} to take an incoming call.
     *
     * @param sessionId a session id which is obtained from {@link ImsManager#open}
     * @param incomingCallIntent the incoming call broadcast intent
     * @param listener to listen to the call events from {@link ImsCall}
     * @return a {@link ImsCall} object
     * @throws ImsException if calling the IMS service results in an error
     */
    public ImsCall takeCall(int sessionId, Intent incomingCallIntent,
            ImsCall.Listener listener) throws ImsException {
        if (DBG) {
            log("takeCall :: sessionId=" + sessionId
                    + ", incomingCall=" + incomingCallIntent);
        }

        checkAndThrowExceptionIfServiceUnavailable();

        if (incomingCallIntent == null) {
            throw new ImsException("Can't retrieve session with null intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        int incomingServiceId = getImsSessionId(incomingCallIntent);

        if (sessionId != incomingServiceId) {
            throw new ImsException("Service id is mismatched in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        String callId = getCallId(incomingCallIntent);

        if (callId == null) {
            throw new ImsException("Call ID missing in the incoming call intent",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        try {
            IImsCallSession session = mImsServiceProxy.getPendingCallSession(sessionId, callId);

            if (session == null) {
                throw new ImsException("No pending session for the call",
                        ImsReasonInfo.CODE_LOCAL_NO_PENDING_CALL);
            }

            ImsCall call = new ImsCall(mContext, session.getCallProfile());

            call.attachSession(new ImsCallSession(session));
            call.setListener(listener);

            return call;
        } catch (Throwable t) {
            throw new ImsException("takeCall()", t, ImsReasonInfo.CODE_UNSPECIFIED);
        }
    }

    /**
     * Gets the config interface to get/set service/capability parameters.
     *
     * @return the ImsConfig instance.
     * @throws ImsException if getting the setting interface results in an error.
     */
    public ImsConfig getConfigInterface() throws ImsException {
        if (mConfig != null && mConfig.isBinderAlive()) {
            return mConfig;
        }

        checkAndThrowExceptionIfServiceUnavailable();
        try {
            IImsConfig config = mImsServiceProxy.getConfigInterface();
            if (config == null) {
                throw new ImsException("getConfigInterface()",
                        ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
            }
            mConfig = new ImsConfig(config, mContext);
        } catch (RemoteException e) {
            throw new ImsException("getConfigInterface()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return mConfig;
    }

    /**
     * Set the TTY mode. This is the actual tty mode (varies depending on peripheral status)
     */
    public void setTtyMode(int ttyMode) throws ImsException {
        if (!getBooleanCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL)) {
            setAdvanced4GMode((ttyMode == TelecomManager.TTY_MODE_OFF) &&
                    isEnhanced4gLteModeSettingEnabledByUser());
        }
    }

    /**
     * Sets the UI TTY mode. This is the preferred TTY mode that the user sets in the call
     * settings screen.
     */
    public void setUiTTYMode(Context context, int uiTtyMode, Message onComplete)
            throws ImsException {

        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsServiceProxy.setUiTTYMode(uiTtyMode, onComplete);
        } catch (RemoteException e) {
            throw new ImsException("setTTYMode()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    private ImsReasonInfo makeACopy(ImsReasonInfo imsReasonInfo) {
        Parcel p = Parcel.obtain();
        imsReasonInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        ImsReasonInfo clonedReasonInfo = ImsReasonInfo.CREATOR.createFromParcel(p);
        p.recycle();
        return clonedReasonInfo;
    }

    /**
     * Get Recent IMS Disconnect Reasons.
     *
     * @return ArrayList of ImsReasonInfo objects. MAX size of the arraylist
     * is MAX_RECENT_DISCONNECT_REASONS. The objects are in the
     * chronological order.
     */
    public ArrayList<ImsReasonInfo> getRecentImsDisconnectReasons() {
        ArrayList<ImsReasonInfo> disconnectReasons = new ArrayList<>();

        for (ImsReasonInfo reason : mRecentDisconnectReasons) {
            disconnectReasons.add(makeACopy(reason));
        }
        return disconnectReasons;
    }

    public int getImsServiceStatus() throws ImsException {
        return mImsServiceProxy.getFeatureStatus();
    }

    /**
     * Get the boolean config from carrier config manager.
     *
     * @param key config key defined in CarrierConfigManager
     * @return boolean value of corresponding key.
     */
    private boolean getBooleanCarrierConfig(String key) {
        int[] subIds = SubscriptionManager.getSubId(mPhoneId);
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (subIds != null && subIds.length >= 1) {
            subId = subIds[0];
        }
        PersistableBundle b = null;
        if (mConfigManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            b = mConfigManager.getConfigForSubId(subId);
        }
        if (b != null) {
            return b.getBoolean(key);
        } else {
            // Return static default defined in CarrierConfigManager.
            return CarrierConfigManager.getDefaultConfig().getBoolean(key);
        }
    }

    /**
     * Get the int config from carrier config manager.
     *
     * @param key config key defined in CarrierConfigManager
     * @return integer value of corresponding key.
     */
    private int getIntCarrierConfig(String key) {
        int[] subIds = SubscriptionManager.getSubId(mPhoneId);
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (subIds != null && subIds.length >= 1) {
            subId = subIds[0];
        }
        PersistableBundle b = null;
        if (mConfigManager != null) {
            // If an invalid subId is used, this bundle will contain default values.
            b = mConfigManager.getConfigForSubId(subId);
        }
        if (b != null) {
            return b.getInt(key);
        } else {
            // Return static default defined in CarrierConfigManager.
            return CarrierConfigManager.getDefaultConfig().getInt(key);
        }
    }

    /**
     * Gets the call ID from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the call ID or null if the intent does not contain it
     */
    private static String getCallId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return null;
        }

        return incomingCallIntent.getStringExtra(EXTRA_CALL_ID);
    }

    /**
     * Gets the service type from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the session identifier or -1 if the intent does not contain it
     */
    private static int getImsSessionId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return (-1);
        }

        return incomingCallIntent.getIntExtra(EXTRA_SERVICE_ID, -1);
    }

    /**
     * Checks to see if the ImsService Binder is connected. If it is not, we try to create the
     * connection again.
     */
    private void checkAndThrowExceptionIfServiceUnavailable()
            throws ImsException {
        if (mImsServiceProxy == null || !mImsServiceProxy.isBinderAlive()) {
            createImsService();

            if (mImsServiceProxy == null) {
                throw new ImsException("Service is unavailable",
                        ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
            }
        }
    }

    /**
     * Binds the IMS service to make/receive the call. Supports two methods of exposing an
     * ImsService:
     * 1) com.android.ims.ImsService implementation in ServiceManager (deprecated).
     * 2) android.telephony.ims.ImsService implementation through ImsResolver.
     */
    private void createImsService() {
        if (!mConfigDynamicBind) {
            // Deprecated method of binding
            Rlog.i(TAG, "Creating ImsService using ServiceManager");
            mImsServiceProxy = ImsServiceProxyCompat.create(mPhoneId, mDeathRecipient);
        } else {
            Rlog.i(TAG, "Creating ImsService using ImsResolver");
            mImsServiceProxy = ImsServiceProxy.create(mContext, mPhoneId);
        }
        // Forwarding interface to tell mStatusCallbacks that the Proxy is unavailable.
        mImsServiceProxy.setStatusCallback(new ImsServiceProxy.IFeatureUpdate() {
            @Override
            public void notifyStateChanged() {
                mStatusCallbacks.forEach(ImsServiceProxy.IFeatureUpdate::notifyStateChanged);
            }

            @Override
            public void notifyUnavailable() {
                mStatusCallbacks.forEach(ImsServiceProxy.IFeatureUpdate::notifyUnavailable);
            }
        });
        // We have created a new ImsService connection, signal for re-registration
        synchronized (mHasRegisteredLock) {
            mHasRegisteredForProxy = false;
        }
    }

    /**
     * Creates a {@link ImsCallSession} with the specified call profile.
     * Use other methods, if applicable, instead of interacting with
     * {@link ImsCallSession} directly.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @param profile a call profile to make the call
     */
    private ImsCallSession createCallSession(int serviceId,
            ImsCallProfile profile) throws ImsException {
        try {
            // Throws an exception if the ImsService Feature is not ready to accept commands.
            return new ImsCallSession(mImsServiceProxy.createCallSession(serviceId, profile, null));
        } catch (RemoteException e) {
            Rlog.w(TAG, "CreateCallSession: Error, remote exception: " + e.getMessage());
            throw new ImsException("createCallSession()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);

        }
    }

    private static void log(String s) {
        Rlog.d(TAG, s);
    }

    private static void loge(String s) {
        Rlog.e(TAG, s);
    }

    private static void loge(String s, Throwable t) {
        Rlog.e(TAG, s, t);
    }

    /**
     * Used for turning on IMS.if its off already
     */
    private void turnOnIms() throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsServiceProxy.turnOnIms();
        } catch (RemoteException e) {
            throw new ImsException("turnOnIms() ", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    private boolean isImsTurnOffAllowed() {
        return isTurnOffImsAllowedByPlatform()
                && (!isWfcEnabledByPlatform()
                || !isWfcEnabledByUser());
    }

    private void setLteFeatureValues(boolean turnOn) {
        log("setLteFeatureValues: " + turnOn);
        try {
            ImsConfig config = getConfigInterface();
            if (config != null) {
                config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE,
                        TelephonyManager.NETWORK_TYPE_LTE, turnOn ? 1 : 0, mImsConfigListener);

                if (isVolteEnabledByPlatform()) {
                    boolean ignoreDataEnabledChanged = getBooleanCarrierConfig(
                            CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS);
                    boolean enableViLte = turnOn && isVtEnabledByUser() &&
                            (ignoreDataEnabledChanged || isDataEnabled());
                    config.setFeatureValue(ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE,
                            TelephonyManager.NETWORK_TYPE_LTE,
                            enableViLte ? 1 : 0,
                            mImsConfigListener);
                }
            }
        } catch (ImsException e) {
            loge("setLteFeatureValues: exception ", e);
        }
    }

    private void setAdvanced4GMode(boolean turnOn) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        // if turnOn: first set feature values then call turnOnIms()
        // if turnOff: only set feature values if IMS turn off is not allowed. If turn off is
        // allowed, first call turnOffIms() then set feature values
        if (turnOn) {
            setLteFeatureValues(turnOn);
            log("setAdvanced4GMode: turnOnIms");
            turnOnIms();
        } else {
            if (isImsTurnOffAllowed()) {
                log("setAdvanced4GMode: turnOffIms");
                turnOffIms();
            }
            setLteFeatureValues(turnOn);
        }
    }

    /**
     * Used for turning off IMS completely in order to make the device CSFB'ed.
     * Once turned off, all calls will be over CS.
     */
    private void turnOffIms() throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable();

        try {
            mImsServiceProxy.turnOffIms();
        } catch (RemoteException e) {
            throw new ImsException("turnOffIms() ", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    private void addToRecentDisconnectReasons(ImsReasonInfo reason) {
        if (reason == null) return;
        while (mRecentDisconnectReasons.size() >= MAX_RECENT_DISCONNECT_REASONS) {
            mRecentDisconnectReasons.removeFirst();
        }
        mRecentDisconnectReasons.addLast(reason);
    }

    /**
     * Death recipient class for monitoring IMS service.
     */
    private class ImsServiceDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            mImsServiceProxy = null;
            mUt = null;
            mConfig = null;
            mEcbm = null;
            mMultiEndpoint = null;
        }
    }

    /**
     * Stub implementation of the Registration listener that provides no functionality.
     */
    private class ImsRegistrationListenerBase extends IImsRegistrationListener.Stub {

        @Override
        public void registrationConnected() throws RemoteException {
        }

        @Override
        public void registrationProgressing() throws RemoteException {
        }

        @Override
        public void registrationConnectedWithRadioTech(int imsRadioTech) throws RemoteException {
        }

        @Override
        public void registrationProgressingWithRadioTech(int imsRadioTech) throws RemoteException {
        }

        @Override
        public void registrationDisconnected(ImsReasonInfo imsReasonInfo) throws RemoteException {
        }

        @Override
        public void registrationResumed() throws RemoteException {
        }

        @Override
        public void registrationSuspended() throws RemoteException {
        }

        @Override
        public void registrationServiceCapabilityChanged(int serviceClass, int event)
                throws RemoteException {
        }

        @Override
        public void registrationFeatureCapabilityChanged(int serviceClass, int[] enabledFeatures,
                int[] disabledFeatures) throws RemoteException {
        }

        @Override
        public void voiceMessageCountUpdate(int count) throws RemoteException {
        }

        @Override
        public void registrationAssociatedUriChanged(Uri[] uris) throws RemoteException {
        }

        @Override
        public void registrationChangeFailed(int targetAccessTech, ImsReasonInfo imsReasonInfo)
                throws RemoteException {
        }
    }

    /**
     * Adapter class for {@link IImsRegistrationListener}.
     */
    private class ImsRegistrationListenerProxy extends IImsRegistrationListener.Stub {

        @Deprecated
        public void registrationConnected() {
            if (DBG) {
                log("registrationConnected ::");
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsConnected(
                        ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN));
            }
        }

        @Deprecated
        public void registrationProgressing() {
            if (DBG) {
                log("registrationProgressing ::");
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsProgressing(
                        ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN));
            }
        }

        @Override
        public void registrationConnectedWithRadioTech(int imsRadioTech) {
            // Note: imsRadioTech value maps to RIL_RADIO_TECHNOLOGY
            //       values in ServiceState.java.
            if (DBG) {
                log("registrationConnectedWithRadioTech :: imsRadioTech=" + imsRadioTech);
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsConnected(imsRadioTech));
            }
        }

        @Override
        public void registrationProgressingWithRadioTech(int imsRadioTech) {
            // Note: imsRadioTech value maps to RIL_RADIO_TECHNOLOGY
            //       values in ServiceState.java.
            if (DBG) {
                log("registrationProgressingWithRadioTech :: imsRadioTech=" + imsRadioTech);
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsProgressing(imsRadioTech));
            }
        }

        @Override
        public void registrationDisconnected(ImsReasonInfo imsReasonInfo) {
            if (DBG) {
                log("registrationDisconnected :: imsReasonInfo" + imsReasonInfo);
            }

            addToRecentDisconnectReasons(imsReasonInfo);
            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsDisconnected(imsReasonInfo));
            }
        }

        @Override
        public void registrationResumed() {
            if (DBG) {
                log("registrationResumed ::");
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(ImsConnectionStateListener::onImsResumed);
            }
        }

        @Override
        public void registrationSuspended() {
            if (DBG) {
                log("registrationSuspended ::");
            }

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(ImsConnectionStateListener::onImsSuspended);
            }
        }

        @Override
        public void registrationServiceCapabilityChanged(int serviceClass, int event) {
            log("registrationServiceCapabilityChanged :: serviceClass=" +
                    serviceClass + ", event=" + event);

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onImsConnected(
                        ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN));
            }
        }

        @Override
        public void registrationFeatureCapabilityChanged(int serviceClass,
                int[] enabledFeatures, int[] disabledFeatures) {
            log("registrationFeatureCapabilityChanged :: serviceClass=" +
                    serviceClass);

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onFeatureCapabilityChanged(serviceClass,
                        enabledFeatures, disabledFeatures));
            }
        }

        @Override
        public void voiceMessageCountUpdate(int count) {
            log("voiceMessageCountUpdate :: count=" + count);

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onVoiceMessageCountChanged(count));
            }
        }

        @Override
        public void registrationAssociatedUriChanged(Uri[] uris) {
            if (DBG) log("registrationAssociatedUriChanged ::");

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.registrationAssociatedUriChanged(uris));
            }
        }

        @Override
        public void registrationChangeFailed(int targetAccessTech, ImsReasonInfo imsReasonInfo) {
            if (DBG) log("registrationChangeFailed :: targetAccessTech=" + targetAccessTech +
                    ", imsReasonInfo=" + imsReasonInfo);

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onRegistrationChangeFailed(targetAccessTech,
                        imsReasonInfo));
            }
        }
    }

    // New API for Registration, uses ImsConnectionStateListener for backwards compatibility with
    // deprecated APIs.
    private class ImsRegistrationCallback extends IImsRegistrationCallback.Stub {

        @Override
        public void onRegistered(int imsRadioTech) {
            if (DBG) log("onRegistered ::");

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onRegistered(imsRadioTech));
            }
        }

        @Override
        public void onRegistering(int imsRadioTech) {
            if (DBG) log("onRegistering ::");

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onRegistering(imsRadioTech));
            }
        }

        @Override
        public void onDeregistered(ImsReasonInfo imsReasonInfo) {
            if (DBG) log("onDeregistered ::");

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onDeregistered(imsReasonInfo));
            }
        }

        @Override
        public void onTechnologyChangeFailed(int targetRadioTech, ImsReasonInfo imsReasonInfo) {
            if (DBG) log("onTechnologyChangeFailed :: targetAccessTech=" + targetRadioTech +
                    ", imsReasonInfo=" + imsReasonInfo);

            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onTechnologyChangeFailed(targetRadioTech,
                        imsReasonInfo));
            }
        }

        @Override
        public void onSubscriberAssociatedUriChanged(Uri[] uris) {
            if (DBG) log("onSubscriberAssociatedUriChanged");
            synchronized (mRegistrationListeners) {
                mRegistrationListeners.forEach(l -> l.onSubscriberAssociatedUriChanged(uris));
            }
        }
    }

    /**
     * Gets the ECBM interface to request ECBM exit.
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @return the ECBM interface instance
     * @throws ImsException if getting the ECBM interface results in an error
     */
    public ImsEcbm getEcbmInterface(int serviceId) throws ImsException {
        if (mEcbm != null && mEcbm.isBinderAlive()) {
            return mEcbm;
        }

        checkAndThrowExceptionIfServiceUnavailable();
        try {
            IImsEcbm iEcbm = mImsServiceProxy.getEcbmInterface();

            if (iEcbm == null) {
                throw new ImsException("getEcbmInterface()",
                        ImsReasonInfo.CODE_ECBM_NOT_SUPPORTED);
            }
            mEcbm = new ImsEcbm(iEcbm);
        } catch (RemoteException e) {
            throw new ImsException("getEcbmInterface()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
        return mEcbm;
    }

    public void sendSms(int token, int messageRef, String format, String smsc, boolean isRetry,
            byte[] pdu) throws ImsException {
        try {
            mImsServiceProxy.sendSms(token, messageRef, format, smsc, isRetry, pdu);
        } catch (RemoteException e) {
            throw new ImsException("sendSms()", e, ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    public void acknowledgeSms(int token, int messageRef, int result) throws ImsException {
        try {
            mImsServiceProxy.acknowledgeSms(token, messageRef, result);
        } catch (RemoteException e) {
            throw new ImsException("acknowledgeSms()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    public void acknowledgeSmsReport(int token, int messageRef, int result) throws  ImsException{
        try {
            mImsServiceProxy.acknowledgeSmsReport(token, messageRef, result);
        } catch (RemoteException e) {
            throw new ImsException("acknowledgeSmsReport()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    public String getSmsFormat() throws ImsException{
        try {
            return mImsServiceProxy.getSmsFormat();
        } catch (RemoteException e) {
            throw new ImsException("getSmsFormat()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    public void setSmsListener(IImsSmsListener listener) throws ImsException {
        try {
            mImsServiceProxy.setSmsListener(listener);
        } catch (RemoteException e) {
            throw new ImsException("setSmsListener()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }
    /**
     * Gets the Multi-Endpoint interface to subscribe to multi-enpoint notifications..
     *
     * @param serviceId a service id which is obtained from {@link ImsManager#open}
     * @return the multi-endpoint interface instance
     * @throws ImsException if getting the multi-endpoint interface results in an error
     */
    public ImsMultiEndpoint getMultiEndpointInterface(int serviceId) throws ImsException {
        if (mMultiEndpoint != null && mMultiEndpoint.isBinderAlive()) {
            return mMultiEndpoint;
        }

        checkAndThrowExceptionIfServiceUnavailable();
        try {
            IImsMultiEndpoint iImsMultiEndpoint = mImsServiceProxy.getMultiEndpointInterface();

            if (iImsMultiEndpoint == null) {
                throw new ImsException("getMultiEndpointInterface()",
                        ImsReasonInfo.CODE_MULTIENDPOINT_NOT_SUPPORTED);
            }
            mMultiEndpoint = new ImsMultiEndpoint(iImsMultiEndpoint);
        } catch (RemoteException e) {
            throw new ImsException("getMultiEndpointInterface()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }

        return mMultiEndpoint;
    }

    /**
     * Resets ImsManager settings back to factory defaults.
     *
     * @deprecated Doesn't support MSIM devices. Use {@link #factoryReset()} instead.
     *
     * @hide
     */
    public static void factoryReset(Context context) {
        ImsManager mgr = ImsManager.getInstance(context,
                SubscriptionManager.getDefaultVoicePhoneId());
        if (mgr != null) {
            mgr.factoryReset();
        }
        loge("factoryReset: ImsManager null.");
    }

    /**
     * Resets ImsManager settings back to factory defaults.
     *
     * @hide
     */
    public void factoryReset() {
        // Set VoLTE to default
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                booleanToPropertyString(getBooleanCarrierConfig(
                        CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL)));

        // Set VoWiFi to default
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_ENABLED,
                booleanToPropertyString(getBooleanCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL)));

        // Set VoWiFi mode to default
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_MODE,
                Integer.toString(getIntCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT)));

        // Set VoWiFi roaming to default
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.WFC_IMS_ROAMING_ENABLED,
                booleanToPropertyString(getBooleanCarrierConfig(
                        CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL)));

        // Set VT to default
        SubscriptionManager.setSubscriptionProperty(getSubId(),
                SubscriptionManager.VT_IMS_ENABLED, booleanToPropertyString(true));

        // Push settings to ImsConfig
        updateImsServiceConfig(true);
    }

    private boolean isDataEnabled() {
        return new TelephonyManager(mContext, getSubId()).isMobileDataEnabled();
    }

    private boolean isVolteProvisioned() {
        return getProvisionedBoolNoException(
                ImsConfig.ConfigConstants.VLT_SETTING_ENABLED);
    }

    private boolean isWfcProvisioned() {
        return getProvisionedBoolNoException(
                ImsConfig.ConfigConstants.VOICE_OVER_WIFI_SETTING_ENABLED);
    }

    private boolean isVtProvisioned() {
        return getProvisionedBoolNoException(
                ImsConfig.ConfigConstants.LVC_SETTING_ENABLED);
    }

    private static String booleanToPropertyString(boolean bool) {
        return bool ? "1" : "0";
    }


    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ImsManager:");
        pw.println("  mPhoneId = " + mPhoneId);
        pw.println("  mConfigUpdated = " + mConfigUpdated);
        pw.println("  mImsServiceProxy = " + mImsServiceProxy);
        pw.println("  mDataEnabled = " + isDataEnabled());
        pw.println("  ignoreDataEnabledChanged = " + getBooleanCarrierConfig(
                CarrierConfigManager.KEY_IGNORE_DATA_ENABLED_CHANGED_FOR_VIDEO_CALLS));

        pw.println("  isGbaValid = " + isGbaValid());
        pw.println("  isImsTurnOffAllowed = " + isImsTurnOffAllowed());
        pw.println("  isNonTtyOrTtyOnVolteEnabled = " + isNonTtyOrTtyOnVolteEnabled());

        pw.println("  isVolteEnabledByPlatform = " + isVolteEnabledByPlatform());
        pw.println("  isVolteProvisionedOnDevice = " + isVolteProvisionedOnDevice());
        pw.println("  isEnhanced4gLteModeSettingEnabledByUser = " +
                isEnhanced4gLteModeSettingEnabledByUser());
        pw.println("  isVtEnabledByPlatform = " + isVtEnabledByPlatform());
        pw.println("  isVtEnabledByUser = " + isVtEnabledByUser());

        pw.println("  isWfcEnabledByPlatform = " + isWfcEnabledByPlatform());
        pw.println("  isWfcEnabledByUser = " + isWfcEnabledByUser());
        pw.println("  getWfcMode = " + getWfcMode());
        pw.println("  isWfcRoamingEnabledByUser = " + isWfcRoamingEnabledByUser());

        pw.println("  isVtProvisionedOnDevice = " + isVtProvisionedOnDevice());
        pw.println("  isWfcProvisionedOnDevice = " + isWfcProvisionedOnDevice());
        pw.flush();
    }
}
