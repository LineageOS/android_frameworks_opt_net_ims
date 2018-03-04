package com.android.ims;

//import android.common.HwFrameworkFactory;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsServiceProxy;
import android.telephony.ims.ImsServiceProxyCompat;
import com.android.ims.internal.IImsServiceController;
import com.google.android.mms.pdu.CharacterSets;
//import com.android.internal.telephony.HwModemCapability;

public class HwImsManager {
    private static final String DESCRIPTOR = "com.android.internal.telephony.IHwTelephony";
    public static final String ACTION_IMS_FACTORY_RESET = "com.huawei.ACTION_NETWORK_FACTORY_RESET";
    private static final boolean DBG = true;
    private static final int DEFAULT_WFC_MODE = 2;
    private static final boolean FEATURE_DUAL_VOWIFI = SystemProperties.getBoolean("ro.config.hw_dual_vowifi", true);
    private static final boolean FEATURE_SHOW_VOLTE_SWITCH = SystemProperties.getBoolean("ro.config.hw_volte_show_switch", true);
    private static final boolean FEATURE_VOLTE_DYN = SystemProperties.getBoolean("ro.config.hw_volte_dyn", false);
    public static final String HW_QCOM_VOLTE_USER_SWITCH = "volte_vt_enabled";
    public static final String HW_VOLTE_USER_SWITCH = "hw_volte_user_switch";
    private static final String[] HW_VOLTE_USER_SWITCH_DUALIMS = new String[]{"hw_volte_user_switch_0", "hw_volte_user_switch_1"};
    private static final String IMS_SERVICE = "ims";
    private static final int INT_INVALID_VALUE = -1;
    public static final String KEY_CARRIER_DEFAULT_VOLTE_SWITCH_ON_BOOL = "carrier_default_volte_switch_on_bool";
    public static final String KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT = "carrier_default_wfc_ims_roaming_mode_int";
    public static final String PROP_VOLTE_ENABLE = "ro.config.hw_volte_on";
    public static final String PROP_VOWIFI_ENABLE = "ro.config.hw_vowifi";
    public static final String SUBID = "subId";
    private static final int SUBID_0 = 0;
    private static final int SUBID_1 = 1;
    private static final String TAG = "HwImsManager";
    private static final int VOWIFI_PREFER_INVALID = 3;
    private static final String[] VT_IMS_ENABLED_DUALIMS = new String[]{"vt_ims_enabled_0", "vt_ims_enabled_1"};
    public static final String WFC_IMS_ENABLED = "wfc_ims_enabled";
    private static final String[] WFC_IMS_ENABLED_DUALIMS = new String[]{"wfc_ims_enabled_0", "wfc_ims_enabled_1"};
    public static final String WFC_IMS_MODE = "wfc_ims_mode";
    private static final String[] WFC_IMS_MODE_DUALIMS = new String[]{"wfc_ims_mode_0", "wfc_ims_mode_1"};
    public static final String WFC_IMS_ROAMING_ENABLED = "wfc_ims_roaming_enabled";
    private static final String[] WFC_IMS_ROAMING_ENABLED_DUALIMS = new String[]{"wfc_ims_roaming_enabled_0", "wfc_ims_roaming_enabled_1"};
    public static final String WFC_IMS_ROAMING_MODE = "wfc_ims_roaming_mode";
    private static final String[] WFC_IMS_ROAMING_MODE_DUALIMS = new String[]{"wfc_ims_roaming_mode_0", "wfc_ims_roaming_mode_1"};
    private static int[] userSelectWfcMode = new int[]{3, 3};

    public static boolean isEnhanced4gLteModeSettingEnabledByUser(Context context, int subId) {
//        return HwImsManagerInner.isEnhanced4gLteModeSettingEnabledByUser(context, subId);
        boolean z = true;
        log("isEnhanced4gLteModeSettingEnabledByUser :: subId -> " + subId);
        if (isValidParameter(context, subId)) {
            int enabled;
            int currentSubId = subId;
            String dbName = HW_VOLTE_USER_SWITCH;
            if (isDualImsAvailable()) {
                dbName = HW_VOLTE_USER_SWITCH_DUALIMS[subId];
            } else {
                currentSubId = getDefault4GSlotId();
                log("isEnhanced4gLteModeSettingEnabledByUser :: dual-ims is not support, subId is main-subId");
            }
            if (FEATURE_VOLTE_DYN) {
                if (!getBooleanCarrierConfig(context, "carrier_volte_available_bool", currentSubId)) {
                    log("KEY_CARRIER_VOLTE_AVAILABLE_BOOL is false, return false");
                    return false;
                } else if (getBooleanCarrierConfig(context, "carrier_volte_show_switch_bool", currentSubId)) {
                    enabled = System.getInt(context.getContentResolver(), dbName, getBooleanCarrierConfig(context, KEY_CARRIER_DEFAULT_VOLTE_SWITCH_ON_BOOL, currentSubId) ? 1 : 0);
                    log("FEATURE_VOLTE_DYN is true, result -> " + enabled + "subId ->" + currentSubId);
                } else {
                    log("KEY_CARRIER_VOLTE_SHOW_SWITCH_BOOL is false, return true");
                    return true;
                }
            } else if (!FEATURE_SHOW_VOLTE_SWITCH) {
                return true;
            } else {
                enabled = System.getInt(context.getContentResolver(), dbName, 0);
            }
            log("isEnhanced4gLteModeSettingEnabledByUser result -> " + enabled + "currentSubId -> " + currentSubId);
            if (enabled != 1) {
                z = false;
            }
            return z;
        }
        loge("subId is wrong or context is null, the result is false, subID is" + subId);
        return false;
    }

    public static boolean isNonTtyOrTtyOnVolteEnabled(Context context, int subId) {
//        return HwImsManagerInner.isNonTtyOrTtyOnVolteEnabled(context, subId);
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (!isDualImsAvailable()) {
            log("isNonTtyOrTtyOnVolteEnabled :: dual-ims is not support");
            return ImsManager.isNonTtyOrTtyOnVolteEnabled(context);
        } else if (getBooleanCarrierConfig(context, "carrier_volte_tty_supported_bool", subId)) {
            return true;
        } else {
            boolean result = Secure.getInt(context.getContentResolver(), "preferred_tty_mode", 0) == 0;
            log("isNonTtyOrTtyOnVolteEnabled result -> " + result + SUBID + subId);
            return result;
        }
    }

    public static boolean isVolteEnabledByPlatform(Context context, int subId) {
//        return HwImsManagerInner.isVolteEnabledByPlatform(context, subId);
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (!isDualImsAvailable()) {
            log("isVolteEnabledByPlatform :: dual-ims is not support");
            return ImsManager.isVolteEnabledByPlatform(context);
        } else if (SystemProperties.getBoolean(PROP_VOLTE_ENABLE, false)) {
            boolean result1 = context.getResources().getBoolean(17956922);
            boolean result2 = getBooleanCarrierConfig(context, "carrier_volte_available_bool", subId);
            boolean result3 = isGbaValid(context, subId);
            log("Volte sim adp : Device =" + result1 + " XML_CarrierConfig =" + result2 + " GbaValid =" + result3 + " subId =" + subId);
            boolean z = (result1 && result2) ? result3 : false;
            return z;
        } else {
            log("hw_volte_on is false");
            return false;
        }
    }

    public static boolean isVtEnabledByPlatform(Context context, int subId) {
//        return HwImsManagerInner.isVtEnabledByPlatform(context, subId);
        boolean z = false;
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (isDualImsAvailable()) {
            if (context.getResources().getBoolean(17956923) && getBooleanCarrierConfig(context, "carrier_vt_available_bool", subId)) {
                z = isGbaValid(context, subId);
            }
            return z;
        } else {
            log("isVtEnabledByPlatform :: dual-ims is not support");
            return ImsManager.isVtEnabledByPlatform(context);
        }
    }

    public static boolean isVtEnabledByUser(Context context, int subId) {
//        return HwImsManagerInner.isVtEnabledByUser(context, subId);
        boolean z = true;
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (isDualImsAvailable()) {
            if (Global.getInt(context.getContentResolver(), VT_IMS_ENABLED_DUALIMS[subId], 1) != 1) {
                z = false;
            }
            return z;
        } else {
            log("isVtEnabledByUser :: dual-ims is not support");
            return ImsManager.isVtEnabledByUser(context);
        }
    }

    public static boolean isWfcEnabledByUser(Context context, int subId) {
//        return HwImsManagerInner.isWfcEnabledByUser(context, subId);
        boolean z = true;
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
            int i;
            boolean defaultValue = getBooleanCarrierConfig(context, "carrier_default_wfc_ims_enabled_bool", subId);
            ContentResolver contentResolver = context.getContentResolver();
            String str = WFC_IMS_ENABLED_DUALIMS[subId];
            if (defaultValue) {
                i = 1;
            } else {
                i = 0;
            }
            int enabled = Global.getInt(contentResolver, str, i);
            log("isWfcEnabledByUser subId ->" + subId + "result -> " + enabled + "defaultvalue ->" + defaultValue);
            if (enabled != 1) {
                z = false;
            }
            return z;
        } else {
            log("isWfcEnabledByUser :: dual-ims is not support");
            if (getDefault4GSlotId() == subId) {
                return ImsManager.isWfcEnabledByUser(context);
            }
            loge("isWfcEnabledByUser error, subId should be the mainsubId");
            return false;
        }
    }

    public static void setWfcSetting(Context context, boolean enabled, int subId) {
//        HwImsManagerInner.setWfcSetting(context, enabled, subId);
        int i = 1;
        int i2 = 0;
        if (isValidParameter(context, subId)) {
            if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
                log("setwfcSetting subId ->" + subId + "result ->" + enabled);
                Global.putInt(context.getContentResolver(), WFC_IMS_ENABLED_DUALIMS[subId], enabled ? 1 : 0);
                ImsManager imsManager = ImsManager.getInstance(context, subId);
                if (imsManager != null) {
                    try {
                        boolean isNetworkRoaming;
                        ImsConfig config = imsManager.getConfigInterface();
                        TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
                        if (tm != null) {
                            isNetworkRoaming = tm.isNetworkRoaming(subId);
                        } else {
                            isNetworkRoaming = false;
                        }
                        Boolean isRoaming = Boolean.valueOf(isNetworkRoaming);
                        Boolean isVowifiEnable = Boolean.valueOf(isWfcEnabledByPlatform(context, subId));
                        if (isVowifiEnable.booleanValue() && 3 == userSelectWfcMode[subId]) {
                            userSelectWfcMode[subId] = getWfcMode(context, isRoaming.booleanValue(), subId);
                        }
                        if (enabled) {
                            i2 = 1;
                        }
                        config.setFeatureValue(2, 18, i2, imsManager.getImsConfigListener());
                        if (enabled) {
                            if (isVowifiEnable.booleanValue()) {
                                log("isVowifiEnable = true, setWfcModeInternal - setting = " + userSelectWfcMode[subId] + "subId = " + subId);
                                setWfcModeInternal(context, userSelectWfcMode[subId], subId);
                            }
                            log("setWfcSetting() : turnOnIms");
                            turnOnIms(imsManager, context, subId);
                        } else if (getBooleanCarrierConfig(context, "carrier_allow_turnoff_ims_bool", subId) && !(isVolteEnabledByPlatform(context, subId) && (isEnhanced4gLteModeSettingEnabledByUser(context, subId) ^ true) == false)) {
                            log("setWfcSetting() : imsServiceAllowTurnOff -> turnOffIms");
                            turnOffIms(imsManager, context, subId);
                        }
                        if (enabled) {
                            i = getWfcMode(context, isRoaming.booleanValue(), subId);
                        }
                        setWfcModeInternal(context, i, subId);
                    } catch (ImsException e) {
                        loge("setWfcSetting(): ", e);
                    }
                }
            } else {
                log("setWfcSetting :: dual-ims is not support");
                if (getDefault4GSlotId() != subId) {
                    loge("setWfcSetting error, subId should be the mainsubId");
                    return;
                }
                ImsManager.setWfcSetting(context, enabled);
            }
            return;
        }
        loge("subId is wrong or context is null, subID is" + subId);
    }

    public static int getWfcMode(Context context, int subId) {
//        return HwImsManagerInner.getWfcMode(context, subId);
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is deault_wfc_mode, subID is" + subId);
            return 2;
        } else if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
            int setting = Global.getInt(context.getContentResolver(), WFC_IMS_MODE_DUALIMS[subId], getIntCarrierConfig(context, "carrier_default_wfc_ims_mode_int", subId));
            log("getWfcMode - setting=" + setting + "subId =" + subId);
            return setting;
        } else {
            log("getWfcMode :: dual-ims is not support");
            if (getDefault4GSlotId() == subId) {
                return ImsManager.getWfcMode(context);
            }
            loge("getWfcMode error, subId should be the mainsubId");
            return 2;
        }
    }

    public static void setWfcMode(Context context, int wfcMode, int subId) {
//        HwImsManagerInner.setWfcMode(context, wfcMode, subId);
        if (isValidParameter(context, subId)) {
            if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
                log("setWfcMode - subId=" + subId + "setting=" + wfcMode);
                Global.putInt(context.getContentResolver(), WFC_IMS_MODE_DUALIMS[subId], wfcMode);
                if (Boolean.valueOf(isWfcEnabledByPlatform(context, subId)).booleanValue()) {
                    userSelectWfcMode[subId] = wfcMode;
                }
                setWfcModeInternal(context, wfcMode, subId);
            } else {
                log("setWfcMode :: dual-ims is not support");
                if (getDefault4GSlotId() != subId) {
                    loge("setWfcMode error, subId should be the mainsubId");
                    return;
                }
                ImsManager.setWfcMode(context, wfcMode);
            }
            return;
        }
        loge("subId is wrong or context is null, subID is" + subId);
    }

    private static void setWfcModeInternal(Context context, final int wfcMode, int subId) {
        final ImsManager imsManager = ImsManager.getInstance(context, subId);
        if (imsManager != null) {
            int value = wfcMode;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        imsManager.getConfigInterface().setProvisionedValue(27, wfcMode);
                    } catch (ImsException e) {
                        loge("setWfcModeInternal(): ", e);
                    }
                }
            }).start();
        }
    }

    public static int getWfcMode(Context context, boolean roaming, int subId) {
//        return HwImsManagerInner.getWfcMode(context, roaming, subId);
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is deault_wfc_mode, subID is" + subId);
            return 2;
        } else if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
            int setting;
            if (checkCarrierConfigKeyExist(context, KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT, subId).booleanValue() && (roaming ^ true) == false) {
                setting = Global.getInt(context.getContentResolver(), WFC_IMS_ROAMING_MODE_DUALIMS[subId], getIntCarrierConfig(context, KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT, subId));
                log("getWfcMode (roaming) - setting=" + setting + "subId=" + subId);
            } else {
                setting = Global.getInt(context.getContentResolver(), WFC_IMS_MODE_DUALIMS[subId], getIntCarrierConfig(context, "carrier_default_wfc_ims_mode_int", subId));
                log("getWfcMode - setting=" + setting + "subId=" + subId);
            }
            return setting;
        } else {
            log("getWfcMode :: dual-ims is not supportroaming is " + roaming);
            if (getDefault4GSlotId() == subId) {
                return ImsManager.getWfcMode(context, roaming);
            }
            loge("getWfcMode error, subId should be the mainsubId");
            return 2;
        }
    }

    public static void setWfcMode(Context context, int wfcMode, boolean roaming, int subId) {
//        HwImsManagerInner.setWfcMode(context, wfcMode, roaming, subId);
        if (isValidParameter(context, subId)) {
            if (!isDualImsAvailable() || (FEATURE_DUAL_VOWIFI ^ true) != false) {
                log("setWfcMode :: dual-ims is not supportroaming is " + roaming);
                if (getDefault4GSlotId() != subId) {
                    loge("setWfcMode error, subId should be the mainsubId");
                    return;
                }
                ImsManager.setWfcMode(context, wfcMode, roaming);
            } else if (isWfcEnabledByPlatform(context, subId)) {
                boolean hasCust = checkCarrierConfigKeyExist(context, KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT, subId).booleanValue();
                if (hasCust && roaming) {
                    log("setWfcMode(roaming) - setting=" + wfcMode + "subId=" + subId);
                    Global.putInt(context.getContentResolver(), WFC_IMS_ROAMING_MODE_DUALIMS[subId], wfcMode);
                } else {
                    log("setWfcMode - setting=" + wfcMode + "subId=" + subId);
                    Global.putInt(context.getContentResolver(), WFC_IMS_MODE_DUALIMS[subId], wfcMode);
                }
                userSelectWfcMode[subId] = wfcMode;
                TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
                if (tm == null || roaming != tm.isNetworkRoaming(subId)) {
                    if ((hasCust ^ true) != false) {
                    }
                }
                setWfcModeInternal(context, wfcMode, subId);
            }
            return;
        }
        loge("subId is wrong or context is null, subID is" + subId);
    }

    public static boolean isWfcRoamingEnabledByUser(Context context, int subId) {
//        return HwImsManagerInner.isWfcRoamingEnabledByUser(context, subId);
        boolean z = true;
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
            int i;
            ContentResolver contentResolver = context.getContentResolver();
            String str = WFC_IMS_ROAMING_ENABLED_DUALIMS[subId];
            if (getBooleanCarrierConfig(context, "carrier_default_wfc_ims_roaming_enabled_bool", subId)) {
                i = 1;
            } else {
                i = 0;
            }
            if (Global.getInt(contentResolver, str, i) != 1) {
                z = false;
            }
            return z;
        } else {
            log("isWfcRoamingEnabledByUser :: dual-ims is not support");
            if (getDefault4GSlotId() == subId) {
                return ImsManager.isWfcRoamingEnabledByUser(context);
            }
            loge("isWfcRoamingEnabledByUser error, subId should be the mainsubId");
            return false;
        }
    }

    private static void setWfcRoamingSettingInternal(Context context, boolean enabled, int subId) {
        final ImsManager imsManager = ImsManager.getInstance(context, subId);
        if (imsManager != null) {
            int value;
            if (enabled) {
                value = 1;
            } else {
                value = 0;
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        imsManager.getConfigInterface().setProvisionedValue(26, value);
                    } catch (ImsException e) {
                        loge("setWfcRoamingSettingInternal(): ", e);
                    }
                }
            }).start();
        }
    }

    public static void setWfcRoamingSetting(Context context, boolean enabled, int subId) {
//        HwImsManagerInner.setWfcRoamingSetting(context, enabled, subId);
        if (isValidParameter(context, subId)) {
            if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
                int i;
                ContentResolver contentResolver = context.getContentResolver();
                String str = WFC_IMS_ROAMING_ENABLED_DUALIMS[subId];
                if (enabled) {
                    i = 1;
                } else {
                    i = 0;
                }
                Global.putInt(contentResolver, str, i);
                setWfcRoamingSettingInternal(context, enabled, subId);
            } else {
                log("setWfcRoamingSetting :: dual-ims is not support");
                if (getDefault4GSlotId() != subId) {
                    loge("setWfcRoamingSetting error, subId should be the mainsubId");
                    return;
                }
                ImsManager.setWfcRoamingSetting(context, enabled);
            }
            return;
        }
        loge("subId is wrong or context is null, subID is" + subId);
    }

    public static boolean isWfcEnabledByPlatform(Context context, int subId) {
//        return HwImsManagerInner.isWfcEnabledByPlatform(context, subId);
        if (!isValidParameter(context, subId)) {
            loge("subId is wrong or context is null, the result is false, subID is" + subId);
            return false;
        } else if (!SystemProperties.getBoolean(PROP_VOWIFI_ENABLE, false)) {
            loge("hw_vowifi prop is false, return false");
            return false;
        } else if (isDualImsAvailable() && (FEATURE_DUAL_VOWIFI ^ true) == false) {
            boolean result1 = context.getResources().getBoolean(17956924);
            boolean result2 = getBooleanCarrierConfig(context, "carrier_wfc_ims_available_bool", subId);
            boolean result3 = isGbaValid(context, subId);
            log("Vowifi sim adp : Device =" + result1 + " XML_CarrierConfig =" + result2 + " GbaValid =" + result3 + " subId =" + subId);
            boolean z = (result1 && result2) ? result3 : false;
            return z;
        } else {
            log("isWfcEnabledByPlatform :: dual-ims is not support");
            if (getDefault4GSlotId() == subId) {
                return ImsManager.isWfcEnabledByPlatform(context);
            }
            loge("isWfcEnabledByPlatform error, subId should be the mainsubId, return false");
            return false;
        }
    }

    private static boolean isGbaValid(Context context, int subId) {
        if (!getBooleanCarrierConfig(context, "carrier_ims_gba_required_bool", subId)) {
            return true;
        }
        String efIst = TelephonyManager.getDefault().getIsimIst();
        if (efIst == null) {
            loge("ISF is NULL");
            return true;
        }
        boolean result = efIst.length() > 1 ? (((byte) efIst.charAt(1)) & 2) != 0 : false;
        log("GBA capable=" + result + ", ISF=" + efIst);
        return result;
    }

    public static void updateImsServiceConfig(Context context, int subId, boolean force) {
//        HwImsManagerInner.updateImsServiceConfig(context, subId, force);
        if (isValidParameter(context, subId)) {
            if (!isDualImsAvailable()) {
                log("updateImsServiceConfig :: dual-ims is not support");
                ImsManager.updateImsServiceConfig(context, getDefault4GSlotId(), force);
            } else if (force || TelephonyManager.getDefault().getSimState(subId) == 5) {
                ImsManager imsManager = ImsManager.getInstance(context, subId);
                if (imsManager != null && (!imsManager.getConfigUpdated() || force)) {
                    try {
                        if (((updateVolteFeatureValue(context, subId) | updateVideoCallFeatureValue(context, subId)) | updateWfcFeatureAndProvisionedValues(context, subId)) || (getBooleanCarrierConfig(context, "carrier_allow_turnoff_ims_bool", subId) ^ true) != false) {
                            log("updateImsServiceConfig: turnOnIms, subId is" + subId);
                            turnOnIms(imsManager, context, subId);
                        } else {
                            log("updateImsServiceConfig: turnOffIms, subId is" + subId);
                            turnOffIms(imsManager, context, subId);
                        }
                        imsManager.setConfigUpdated(true);
                    } catch (ImsException e) {
                        loge("updateImsServiceConfig: ", e);
                        imsManager.setConfigUpdated(false);
                    }
                }
            } else {
                log("updateImsServiceConfig: SIM not ready, subId is" + subId);
                return;
            }
            return;
        }
        loge("subId is wrong or context is null, the result is false, subID is" + subId);
    }

    private static boolean updateVolteFeatureValue(Context context, int subId) throws ImsException {
        int i;
        boolean available = isVolteEnabledByPlatform(context, subId);
        boolean enabled = isEnhanced4gLteModeSettingEnabledByUser(context, subId);
        boolean isNonTty = isNonTtyOrTtyOnVolteEnabled(context, subId);
        boolean z = (available && enabled) ? isNonTty : false;
        ImsManager imsManager = ImsManager.getInstance(context, subId);
        log("updateVolteFeatureValue: available = " + available + ", enabled = " + enabled + ", nonTTY = " + isNonTty + ", subId = " + subId);
        ImsConfig configInterface = imsManager.getConfigInterface();
        if (z) {
            i = 1;
        } else {
            i = 0;
        }
        configInterface.setFeatureValue(0, 13, i, imsManager.getImsConfigListener());
        return z;
    }

    private static boolean updateVideoCallFeatureValue(Context context, int subId) throws ImsException {
        boolean isVtEnabledByUser;
        int i;
        boolean available = isVtEnabledByPlatform(context, subId);
        if (isEnhanced4gLteModeSettingEnabledByUser(context, subId)) {
            isVtEnabledByUser = isVtEnabledByUser(context, subId);
        } else {
            isVtEnabledByUser = false;
        }
        boolean isNonTty = isNonTtyOrTtyOnVolteEnabled(context, subId);
        boolean z = (available && isVtEnabledByUser) ? isNonTty : false;
        ImsManager imsManager = ImsManager.getInstance(context, subId);
        log("updateVideoCallFeatureValue: available = " + available + ", enabled = " + isVtEnabledByUser + ", nonTTY = " + isNonTty + ", subId = " + subId);
        ImsConfig configInterface = imsManager.getConfigInterface();
        if (z) {
            i = 1;
        } else {
            i = 0;
        }
        configInterface.setFeatureValue(1, 13, i, imsManager.getImsConfigListener());
        return z;
    }

    private static boolean updateWfcFeatureAndProvisionedValues(Context context, int subId) throws ImsException {
        int i;
        boolean isNetworkRoaming = TelephonyManager.getDefault().isNetworkRoaming(subId);
        boolean available = isWfcEnabledByPlatform(context, subId);
        boolean enabled = isWfcEnabledByUser(context, subId);
        int mode = getWfcMode(context, isNetworkRoaming, subId);
        boolean roaming = isWfcRoamingEnabledByUser(context, subId);
        boolean z = available ? enabled : false;
        ImsManager imsManager = ImsManager.getInstance(context, subId);
        log("updateWfcFeatureAndProvisionedValues: available = " + available + ", enabled = " + enabled + ", mode = " + mode + ", roaming = " + roaming + ", subId = " + subId);
        ImsConfig configInterface = imsManager.getConfigInterface();
        if (z) {
            i = 1;
        } else {
            i = 0;
        }
        configInterface.setFeatureValue(2, 18, i, imsManager.getImsConfigListener());
        if (!z) {
            mode = 1;
            roaming = false;
        }
        setWfcModeInternal(context, mode, subId);
        setWfcRoamingSettingInternal(context, roaming, subId);
        return z;
    }

    private static void turnOnIms(ImsManager imsManager, Context context, int subId) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable(imsManager, context, subId);
        try {
            imsManager.getImsServiceProxy().turnOnIms();
        } catch (RemoteException e) {
            throw new ImsException("turnOnIms() ", e, CharacterSets.DEFAULT_CHARSET);
        }
    }

    private static void turnOffIms(ImsManager imsManager, Context context, int subId) throws ImsException {
        checkAndThrowExceptionIfServiceUnavailable(imsManager, context, subId);
        try {
            imsManager.getImsServiceProxy().turnOffIms();
        } catch (RemoteException e) {
            throw new ImsException("turnOffIms() ", e, CharacterSets.DEFAULT_CHARSET);
        }
    }

    public static void factoryReset(Context context, int subId) {
//        HwImsManagerInner.factoryReset(context, subId);
        int i = 0;
        if (isValidParameter(context, subId)) {
            ContentResolver contentResolver;
            int i2;
            int currentsubId = subId;
            String volteDB = HW_VOLTE_USER_SWITCH_DUALIMS[subId];
            String wfcenabledDB = WFC_IMS_ENABLED_DUALIMS[subId];
            String wfcmodeDB = WFC_IMS_MODE_DUALIMS[subId];
            String wfcroamingmodeDB = WFC_IMS_ROAMING_MODE_DUALIMS[subId];
            String wfcroamingDB = WFC_IMS_ROAMING_ENABLED_DUALIMS[subId];
            if (!isDualImsAvailable()) {
                log("factoryReset :: dual-ims is not support");
                volteDB = HW_VOLTE_USER_SWITCH;
                wfcenabledDB = WFC_IMS_ENABLED;
                wfcmodeDB = WFC_IMS_MODE;
                wfcroamingmodeDB = WFC_IMS_ROAMING_MODE;
                wfcroamingDB = WFC_IMS_ROAMING_ENABLED;
                currentsubId = getDefault4GSlotId();
            }
            if (System.getInt(context.getContentResolver(), volteDB, -1) != -1) {
                contentResolver = context.getContentResolver();
                if (getBooleanCarrierConfig(context, KEY_CARRIER_DEFAULT_VOLTE_SWITCH_ON_BOOL, currentsubId)) {
                    i2 = 1;
                } else {
                    i2 = 0;
                }
                System.putInt(contentResolver, volteDB, i2);
            }
            if (Global.getInt(context.getContentResolver(), HW_QCOM_VOLTE_USER_SWITCH, -1) != -1) {
                contentResolver = context.getContentResolver();
                String str = HW_QCOM_VOLTE_USER_SWITCH;
                if (getBooleanCarrierConfig(context, KEY_CARRIER_DEFAULT_VOLTE_SWITCH_ON_BOOL, currentsubId)) {
                    i2 = 1;
                } else {
                    i2 = 0;
                }
                Global.putInt(contentResolver, str, i2);
            }
            if (hasFiledInDBByFiledName(context, wfcenabledDB)) {
                contentResolver = context.getContentResolver();
                if (getBooleanCarrierConfig(context, "carrier_default_wfc_ims_enabled_bool", currentsubId)) {
                    i2 = 1;
                } else {
                    i2 = 0;
                }
                Global.putInt(contentResolver, wfcenabledDB, i2);
            }
            if (hasFiledInDBByFiledName(context, wfcmodeDB)) {
                Global.putInt(context.getContentResolver(), wfcmodeDB, getIntCarrierConfig(context, "carrier_default_wfc_ims_mode_int", currentsubId));
            }
            if (hasFiledInDBByFiledName(context, wfcroamingmodeDB)) {
                Global.putInt(context.getContentResolver(), wfcroamingmodeDB, getIntCarrierConfig(context, KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT, currentsubId));
            }
            if (hasFiledInDBByFiledName(context, wfcroamingDB)) {
                ContentResolver contentResolver2 = context.getContentResolver();
                if (getBooleanCarrierConfig(context, "carrier_default_wfc_ims_roaming_enabled_bool", currentsubId)) {
                    i = 1;
                }
                Global.putInt(contentResolver2, wfcroamingDB, i);
            }
            updateImsServiceConfig(context, currentsubId, true);
            context.sendBroadcast(new Intent(ACTION_IMS_FACTORY_RESET).putExtra(SUBID, currentsubId));
            return;
        }
        loge("subId is wrong or context is null, subID is" + subId);
    }

    private static boolean hasFiledInDBByFiledName(Context context, String filedName) {
        boolean z = false;
        if (context == null || filedName == null) {
            return false;
        }
        if (Global.getInt(context.getContentResolver(), filedName, -1) != -1) {
            z = true;
        }
        return z;
    }

    public static boolean isDualImsAvailable() {
//        return HwImsManagerInner.isDualImsAvailable();
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            return HwModemCapability.isCapabilitySupport(21);
        }
        log("the device is not support multisim");
        return false;
    }

    private static boolean isValidParameter(Context context, int subId) {
        return (subId == 0 || subId == 1) && context != null;
    }

    public static void setImsSmsConfig(Context context, int setImsSmsConfig, int subId) {
//        HwImsManagerInner.setImsSmsConfig(context, setImsSmsConfig, subId);
        ImsManager imsManager = ImsManager.getInstance(context, subId);
        if (imsManager != null) {
            try {
                ImsConfig config = imsManager.getConfigInterface();
                if (config != null) {
                    config.setImsSmsConfig(imsSmsEnabled);
                }
            } catch (ImsException e) {
                loge("setImsSmsConfig(): ", e);
            }
        }
    }

    public static void getImsSmsConfig(Context context, int subId) {
//        HwImsManagerInner.getImsSmsConfig(context, subId);
        ImsManager imsManager = ImsManager.getInstance(context, subId);
        if (imsManager != null) {
            try {
                ImsConfig config = imsManager.getConfigInterface();
                if (config != null) {
                    config.getImsSmsConfig();
                }
            } catch (ImsException e) {
                loge("getImsSmsConfig(): ", e);
            }
        }
    }

    private static Boolean checkCarrierConfigKeyExist(Context context, String key, int subId) {
        Boolean ifExist = Boolean.valueOf(false);
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService("carrier_config");
        PersistableBundle b = null;
        if (configManager != null) {
            b = configManager.getConfigForSubId(subId);
        }
        if (!(b == null || b.get(key) == null)) {
            log("checkCarrierConfigKeyExist, b.getkey = " + b.get(key) + SUBID + subId);
            ifExist = Boolean.valueOf(true);
        }
        log("carrierConfig key[" + key + "] " + (ifExist.booleanValue() ? "exists" : "does not exist"));
        return ifExist;
    }

    private static boolean getBooleanCarrierConfig(Context context, String key, int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService("carrier_config");
        PersistableBundle b = null;
        if (configManager != null) {
            b = configManager.getConfigForSubId(subId);
        }
        if (b == null || b.get(key) == null) {
            return CarrierConfigManager.getDefaultConfig().getBoolean(key);
        }
        return b.getBoolean(key);
    }

    private static int getIntCarrierConfig(Context context, String key, int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService("carrier_config");
        PersistableBundle b = null;
        if (configManager != null) {
            b = configManager.getConfigForSubId(subId);
        }
        if (b == null || b.get(key) == null) {
            return CarrierConfigManager.getDefaultConfig().getInt(key);
        }
        return b.getInt(key);
    }

    private static void checkAndThrowExceptionIfServiceUnavailable(ImsManager imsManager, Context context, int subId) throws ImsException {
        if (imsManager.getImsServiceProxy() == null || (imsManager.getImsServiceProxy().isBinderAlive() ^ true) != false) {
            createImsService(imsManager, context, subId);
            if (imsManager.getImsServiceProxy() == null) {
                throw new ImsException("Service is unavailable", CharacterSets.DEFAULT_CHARSET);
            }
        }
    }

    private static void createImsService(ImsManager imsManager, Context context, int subId) {
        if (imsManager.isDynamicBinding()) {
            Rlog.i(TAG, "Creating ImsService using ImsResolver");
            imsManager.createImsServiceProxy(getServiceProxy(imsManager, context, subId));
            return;
        }
        Rlog.i(TAG, "Creating ImsService using ServiceManager");
        imsManager.createImsServiceProxy(getServiceProxyCompat(imsManager, subId));
    }

    private static ImsServiceProxyCompat getServiceProxyCompat(ImsManager imsManager, int subId) {
        IBinder binder = ServiceManager.checkService(IMS_SERVICE);
        if (binder != null) {
            try {
                binder.linkToDeath(imsManager.getImsServiceDeathRecipient(), 0);
            } catch (RemoteException e) {
            }
        }
        return new ImsServiceProxyCompat(subId, binder);
    }

    private static ImsServiceProxy getServiceProxy(ImsManager imsManager, Context context, int subId) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService("phone");
        ImsServiceProxy serviceProxy = new ImsServiceProxy(subId, 1);
        IImsServiceController b = tm.getImsServiceControllerAndListen(subId, 1, serviceProxy.getListener());
        if (b != null) {
            serviceProxy.setBinder(b.asBinder());
            serviceProxy.setStatusCallback(new com.android.ims.ImsManager());
            serviceProxy.getFeatureStatus();
        } else {
            Rlog.w(TAG, "getServiceProxy: b is null! Phone Id: " + subId);
        }
        return serviceProxy;
    }

    public static int getDefault4GSlotId() throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            this.mRemote.transact(26, _data, _reply, 0);
            _reply.readException();
            int _result = _reply.readInt();
            return _result;
        } finally {
            _reply.recycle();
            _data.recycle();
        }
    }

    public static boolean setRttEnable(Context context, int subId, int enable) {
        return false;
    }

    public static int getRttEnable(Context context, int subId) {
        return 0;
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
}

