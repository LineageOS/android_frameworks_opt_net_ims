/*
 * Copyright (c) 2014 The Android Open Source Project
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

import android.content.Context;
import android.os.RemoteException;
import android.telephony.Rlog;

import com.android.ims.ImsConfigListener;
import com.android.ims.ImsReasonInfo;
import com.android.ims.internal.IImsConfig;

import java.lang.reflect.Method;

/**
 * Provides APIs to get/set the IMS service feature/capability/parameters.
 * The config items include:
 * 1) Items provisioned by the operator.
 * 2) Items configured by user. Mainly service feature class.
 *
 * @hide
 */
public class ImsConfig {
    private static final String TAG = "ImsConfig";
    private boolean DBG = true;
    private final IImsConfig miConfig;
    private Context mContext;
    private static final String MODIFY_PHONE_STATE = android.Manifest.permission.MODIFY_PHONE_STATE;

    /**
    * Defines IMS service/capability feature constants.
    */
    public static class FeatureConstants {
        public static final int FEATURE_TYPE_UNKNOWN = -1;

        /**
         * FEATURE_TYPE_VOLTE supports features defined in 3GPP and
         * GSMA IR.92 over LTE.
         */
        public static final int FEATURE_TYPE_VOICE_OVER_LTE = 0;

        /**
         * FEATURE_TYPE_LVC supports features defined in 3GPP and
         * GSMA IR.94 over LTE.
         */
        public static final int FEATURE_TYPE_VIDEO_OVER_LTE = 1;

        /**
         * FEATURE_TYPE_VOICE_OVER_WIFI supports features defined in 3GPP and
         * GSMA IR.92 over WiFi.
         */
        public static final int FEATURE_TYPE_VOICE_OVER_WIFI = 2;

        /**
         * FEATURE_TYPE_VIDEO_OVER_WIFI supports features defined in 3GPP and
         * GSMA IR.94 over WiFi.
         */
        public static final int FEATURE_TYPE_VIDEO_OVER_WIFI = 3;

        /**
         * FEATURE_TYPE_UT supports features defined in 3GPP and
         * GSMA IR.92 over LTE.
         */
        public static final int FEATURE_TYPE_UT_OVER_LTE = 4;

       /**
         * FEATURE_TYPE_UT_OVER_WIFI supports features defined in 3GPP and
         * GSMA IR.92 over WiFi.
         */
        public static final int FEATURE_TYPE_UT_OVER_WIFI = 5;
    }

    /**
    * Defines IMS service/capability parameters.
    */
    public static class ConfigConstants {

        // Define IMS config items
        public static final int CONFIG_START = 0;

        // Define operator provisioned config items
        public static final int PROVISIONED_CONFIG_START = CONFIG_START;

        /**
         * AMR CODEC Mode Value set, 0-7 in comma separated sequence.
         * Value is in String format.
         */
        public static final int VOCODER_AMRMODESET = CONFIG_START;

        /**
         * Wide Band AMR CODEC Mode Value set,0-7 in comma separated sequence.
         * Value is in String format.
         */
        public static final int VOCODER_AMRWBMODESET = 1;

        /**
         * SIP Session Timer value (seconds).
         * Value is in Integer format.
         */
        public static final int SIP_SESSION_TIMER = 2;

        /**
         * Minimum SIP Session Expiration Timer in (seconds).
         * Value is in Integer format.
         */
        public static final int MIN_SE = 3;

        /**
         * SIP_INVITE cancellation time out value (in milliseconds). Integer format.
         * Value is in Integer format.
         */
        public static final int CANCELLATION_TIMER = 4;

        /**
         * Delay time when an iRAT transition from eHRPD/HRPD/1xRTT to LTE.
         * Value is in Integer format.
         */
        public static final int TDELAY = 5;

        /**
         * Silent redial status of Enabled (True), or Disabled (False).
         * Value is in Integer format.
         */
        public static final int SILENT_REDIAL_ENABLE = 6;

        /**
         * SIP T1 timer value in milliseconds. See RFC 3261 for define.
         * Value is in Integer format.
         */
        public static final int SIP_T1_TIMER = 7;

        /**
         * SIP T2 timer value in milliseconds.  See RFC 3261 for define.
         * Value is in Integer format.
         */
        public static final int SIP_T2_TIMER  = 8;

         /**
         * SIP TF timer value in milliseconds.  See RFC 3261 for define.
         * Value is in Integer format.
         */
        public static final int SIP_TF_TIMER = 9;

        /**
         * VoLTE status for VLT/s status of Enabled (1), or Disabled (0).
         * Value is in Integer format.
         */
        public static final int VLT_SETTING_ENABLED = 10;

        /**
         * VoLTE status for LVC/s status of Enabled (1), or Disabled (0).
         * Value is in Integer format.
         */
        public static final int LVC_SETTING_ENABLED = 11;
        /**
         * Domain Name for the device to populate the request URI for REGISTRATION.
         * Value is in String format.
         */
        public static final int DOMAIN_NAME = 12;
         /**
         * Device Outgoing SMS based on either 3GPP or 3GPP2 standards.
         * Value is in Integer format. 3GPP2(0), 3GPP(1)
         */
        public static final int SMS_FORMAT = 13;
         /**
         * Turns IMS ON/OFF on the device.
         * Value is in Integer format. ON (1), OFF(0).
         */
        public static final int SMS_OVER_IP = 14;
        /**
         * Requested expiration for Published Online availability.
         * Value is in Integer format.
         */
        public static final int PUBLISH_TIMER = 15;
        /**
         * Requested expiration for Published Offline availability.
         * Value is in Integer format.
         */
        public static final int PUBLISH_TIMER_EXTENDED = 16;
        /**
         * Period of time the capability information of the  contact is cached on handset.
         * Value is in Integer format.
         */
        public static final int CAPABILITIES_CACHE_EXPIRATION = 17;
        /**
         * Peiod of time the availability information of a contact is cached on device.
         * Value is in Integer format.
         */
        public static final int AVAILABILITY_CACHE_EXPIRATION = 18;
        /**
         * Interval between successive capabilities polling.
         * Value is in Integer format.
         */
        public static final int CAPABILITIES_POLL_INTERVAL = 19;
        /**
         * Minimum time between two published messages from the device.
         * Value is in Integer format.
         */
        public static final int SOURCE_THROTTLE_PUBLISH = 20;
        /**
         * The Maximum number of MDNs contained in one Request Contained List.
         * Value is in Integer format.
         */
        public static final int MAX_NUMENTRIES_IN_RCL = 21;
        /**
         * Expiration timer for subscription of a Request Contained List, used in capability polling.
         * Value is in Integer format.
         */
        public static final int CAPAB_POLL_LIST_SUB_EXP = 22;
        /**
         * Applies compression to LIST Subscription.
         * Value is in Integer format. Enable (1), Disable(0).
         */
        public static final int GZIP_FLAG = 23;
        /**
         * VOLTE Status for EAB/s status of Enabled (1), or Disabled (0).
         * Value is in Integer format.
         */
        public static final int EAB_SETTING_ENABLED = 24;
        // Expand the operator config items as needed here, need to change
        // PROVISIONED_CONFIG_END after that.
        public static final int PROVISIONED_CONFIG_END = EAB_SETTING_ENABLED;

        // Expand the operator config items as needed here.
    }

    /**
    * Defines IMS set operation status.
    */
    public static class OperationStatusConstants {
        public static final int UNKNOWN = -1;
        public static final int SUCCESS = 0;
        public static final int FAILED =  1;
        public static final int UNSUPPORTED_CAUSE_NONE = 2;
        public static final int UNSUPPORTED_CAUSE_RAT = 3;
        public static final int UNSUPPORTED_CAUSE_DISABLED = 4;
    }

    /**
     * Defines IMS get operation values.
     */
    public static class OperationValuesConstants {
        /**
         * Values related to Video Quality
         */
        public static final int VIDEO_QUALITY_UNKNOWN = -1;
        public static final int VIDEO_QUALITY_LOW = 0;
        public static final int VIDEO_QUALITY_HIGH = 1;
    }

   /**
    * Defines IMS feature value.
    */
    public static class FeatureValueConstants {
        public static final int OFF = 0;
        public static final int ON = 1;
    }

    public ImsConfig(IImsConfig iconfig, Context context) {
        if (DBG) Rlog.d(TAG, "ImsConfig creates");
        miConfig = iconfig;
        mContext = context;
    }

    /**
     * Gets the value for IMS service/capabilities parameters used by IMS stack.
     * This function should not be called from the mainthread as it could block the
     * mainthread to cause ANR.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return the value in Integer format.
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public int getMasterValue(int item) throws ImsException {
        int ret = 0;
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getMasterValue", Integer.class);
            if (m != null) {
                ret = (Integer)m.invoke(miConfig, item);
            }
        }  catch (Exception e) {
            throw new ImsException("getValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) Rlog.d(TAG, "getMasterValue(): item = " + item + ", ret =" + ret);

        return ret;
    }

    /**
     * Gets the value for IMS service/capabilities parameters used by IMS stack.
     * This function should not be called from the mainthread as it could block the
     * mainthread to cause ANR.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in String format.
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public String getMasterStringValue(int item) throws ImsException {
        String ret = "Unknown";
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getMasterStringValue");
            if (m != null) {
                ret = (String)m.invoke(miConfig, item);
            }
        }  catch (Exception e) {
            throw new ImsException("getMasterStringValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) Rlog.d(TAG, "getMasterStringValue(): item = " + item + ", ret =" + ret);

        return ret;
    }

    /**
     * Gets the provisioned value for IMS service/capabilities parameters used by IMS stack.
     * This function should not be called from the mainthread as it could block the
     * mainthread.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return the value in Integer format.
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public int getProvisionedValue(int item) throws ImsException {
        int ret = 0;
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getProvisionedValue", Integer.class);
            if (m != null) {
                ret = (Integer)m.invoke(miConfig, item);
            }
        }  catch (Exception e) {
            throw new ImsException("getValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) Rlog.d(TAG, "getProvisionedValue(): item = " + item + ", ret =" + ret);

        return ret;
    }

    /**
     * Gets the provisioned value for IMS service/capabilities parameters used by IMS stack.
     * This function should not be called from the mainthread as it could block the
     * mainthread.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @return value in String format.
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public String getProvisionedStringValue(int item) throws ImsException {
        String ret = "Unknown";
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getProvisionedStringValue");
            if (m != null) {
                ret = (String)m.invoke(miConfig, item);
            }
        }  catch (Exception e) {
            throw new ImsException("getProvisionedStringValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) Rlog.d(TAG, "getProvisionedStringValue(): item = " + item + ", ret =" + ret);

        return ret;
    }

    /**
     * Sets the value for IMS service/capabilities parameters by
     * the operator device management entity.
     * This function should not be called from main thread as it could block
     * mainthread.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in Integer format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public int setProvisionedValue(int item, int value)
            throws ImsException {
        mContext.enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        int ret = ImsConfig.OperationStatusConstants.UNKNOWN;
        if (DBG) {
            Rlog.d(TAG, "setProvisionedValue(): item = " + item +
                    "value = " + value);
        }
        try {
            ret = miConfig.setProvisionedValue(item, value);
        }  catch (RemoteException e) {
            throw new ImsException("setProvisionedValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) {
            Rlog.d(TAG, "setProvisionedValue(): item = " + item +
                    " value = " + value + " ret = " + ret);
        }
        return ret;
    }

    /**
     * Sets the value for IMS service/capabilities parameters by
     * the operator device management entity.
     * This function should not be called from main thread as it could block
     * mainthread.
     *
     * @param item, as defined in com.android.ims.ImsConfig#ConfigConstants.
     * @param value in String format.
     * @return as defined in com.android.ims.ImsConfig#OperationStatusConstants
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public int setProvisionedStringValue(int item, String value)
            throws ImsException {
        mContext.enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        int ret = ImsConfig.OperationStatusConstants.UNKNOWN;
        try {
            ret = miConfig.setProvisionedStringValue(item, value);
        }  catch (RemoteException e) {
            throw new ImsException("setProvisionedStringValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
        if (DBG) {
            Rlog.d(TAG, "setProvisionedStringValue(): item = " + item +
                    ", value =" + value);
        }
        return ret;
    }

    /**
     * Gets the value for IMS feature item for specified network type.
     *
     * @param feature, defined as in FeatureConstants.
     * @param network, defined as in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param listener, provided to be notified for the feature on/off status.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public void getFeatureValue(int feature, int network,
            ImsConfigListener listener) throws ImsException {
        if (DBG) {
            Rlog.d(TAG, "getFeatureValue: feature = " + feature + ", network =" + network +
                    ", listener =" + listener);
        }
        try {
            miConfig.getFeatureValue(feature, network, listener);
        } catch (RemoteException e) {
            throw new ImsException("getFeatureValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Sets the value for IMS feature item for specified network type.
     *
     * @param feature, as defined in FeatureConstants.
     * @param network, as defined in android.telephony.TelephonyManager#NETWORK_TYPE_XXX.
     * @param value, as defined in FeatureValueConstants.
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public void setFeatureValue(int feature, int network, int value,
            ImsConfigListener listener) throws ImsException {
        mContext.enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        if (DBG) {
            Rlog.d(TAG, "setFeatureValue: feature = " + feature + ", network =" + network +
                    ", value =" + value + ", listener =" + listener);
        }
        try {
            miConfig.setFeatureValue(feature, network, value, listener);
        } catch (RemoteException e) {
            throw new ImsException("setFeatureValue()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Gets the value for IMS feature item for video call quality.
     *
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public void getVideoQuality(ImsConfigListener listener) throws ImsException {
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getVideoQuality");
            if (m != null) {
                miConfig.getVideoQuality(listener);
            }
        } catch (Exception e) {
            throw new ImsException("getVideoQuality()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Sets the value for IMS feature item video quality.
     *
     * @param quality, defines the value of video quality.
     * @param listener, provided if caller needs to be notified for set result.
     * @return void
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
     public void setVideoQuality(int quality, ImsConfigListener listener) throws ImsException {
        try {
            Method m = miConfig.getClass().getDeclaredMethod("setVideoQuality");
            if (m != null) {
                miConfig.setVideoQuality(quality, listener);
            }
        } catch (Exception e) {
            throw new ImsException("setVideoQuality()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
     }

    /**
     * Gets the value for IMS Volte provisioned.
     * It should be the same as operator provisioned value if applies.
     *
     * @return boolean
     *
     * @throws ImsException if calling the IMS service results in an error.
     */
    public boolean getVolteProvisioned() throws ImsException {
        try {
            Method m = miConfig.getClass().getDeclaredMethod("getVolteProvisioned");
            if (m != null) {
                return (Boolean)m.invoke(miConfig);
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new ImsException("getVolteProvisioned()", e,
                    ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE);
        }
    }
}
