package com.android.ims;

import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Rlog;
import com.android.ims.internal.IImsUt;
import com.android.internal.telephony.HuaweiTelephonyConfigs;
import java.util.HashMap;

public class HwImsUt
  implements AbstractImsUt.ImsUtReference
{
  private static final int CODE_IS_SUPPORT_CFT = 2001;
  private static final int CODE_IS_UT_ENABLE = 2002;
  private static final int CODE_UPDATE_CALLBARRING_OPT = 2004;
  private static final int CODE_UPDATE_CFU_TIMER = 2003;
  private static final boolean DBG = true;
  private static final String DESCRIPTOR = "com.android.ims.internal.IImsUt";
  private static final String IMS_UT_SERVICE_NAME = "ims_ut";
  private static final String TAG = "HwImsUt";
  private static final boolean isHisiPlateform = true;
  private ImsUt mImsUt;
  private int mPhoneId = 0;
  private IImsUt miUt;

  public HwImsUt(ImsUt paramImsUt)
  {
    mImsUt = paramImsUt;
  }

  public HwImsUt(ImsUt paramImsUt, int paramInt)
  {
    mImsUt = paramImsUt;
    mPhoneId = paramInt;
    log("HwImsUt:imsUt = " + paramImsUt + ", mPhoneId = " + mPhoneId);
  }

  public HwImsUt(IImsUt paramIImsUt, ImsUt paramImsUt, int paramInt)
  {
    miUt = paramIImsUt;
    mImsUt = paramImsUt;
    mPhoneId = paramInt;
    log("HwImsUt:miUt=" + miUt + ",mImsUt = " + mImsUt + ", mPhoneId = " + mPhoneId);
  }

  private void log(String paramString)
  {
    Rlog.d("HwImsUt[" + mPhoneId + "]", paramString);
  }

  private void loge(String paramString)
  {
    Rlog.e("HwImsUt[" + mPhoneId + "]", paramString);
  }

  public boolean isSupportCFT()
  {
    boolean bool = true;
    log("isSupportCFT:isHisiPlateform i " + isHisiPlateform);
    if (!isHisiPlateform)
    {
      if (miUt == null)
      {
        loge("The device is not Hisi plateform,but miUt is null");
        return false;
      }
      try
      {
        bool = miUt.isSupportCFT();
        return bool;
      }
      catch (RemoteException localRemoteException1)
      {
        localRemoteException1.printStackTrace();
        return false;
      }
    }
    localParcel2 = Parcel.obtain();
    localParcel1 = Parcel.obtain();
    IBinder localIBinder = ServiceManager.getService("ims_ut");
    log("isSupportCFT");
    if (localIBinder != null) {}
    try
    {
      localParcel2.writeInterfaceToken("com.android.ims.internal.IImsUt");
      localParcel2.writeInt(mPhoneId);
      localIBinder.transact(2001, localParcel2, localParcel1, 0);
      localParcel1.readException();
      int i = localParcel1.readInt();
      if (i == 1) {}
      for (;;)
      {
        return bool;
        bool = false;
      }
      log("isSupportCFT - can't get ims_ut service");
    }
    catch (RemoteException localRemoteException2)
    {
      for (;;)
      {
        localRemoteException2.printStackTrace();
        localParcel1.recycle();
        localParcel2.recycle();
      }
    }
    finally
    {
      localParcel1.recycle();
      localParcel2.recycle();
    }
    return false;
  }

  public boolean isUtEnable()
  {
    boolean bool = true;
    localParcel1 = Parcel.obtain();
    localParcel2 = Parcel.obtain();
    IBinder localIBinder = ServiceManager.getService("ims_ut");
    log("isUtEnable");
    if (localIBinder != null) {}
    try
    {
      localParcel1.writeInterfaceToken("com.android.ims.internal.IImsUt");
      localParcel1.writeInt(mPhoneId);
      localIBinder.transact(2002, localParcel1, localParcel2, 0);
      localParcel2.readException();
      int i = localParcel2.readInt();
      if (i == 1) {}
      for (;;)
      {
        return bool;
        bool = false;
      }
      log("isUtEnable - can't get ims_ut service");
    }
    catch (RemoteException localRemoteException)
    {
      for (;;)
      {
        localRemoteException.printStackTrace();
        localParcel2.recycle();
        localParcel1.recycle();
      }
    }
    finally
    {
      localParcel2.recycle();
      localParcel1.recycle();
    }
    return false;
  }

  public Message popUtMessage(int paramInt)
  {
    Integer localInteger = Integer.valueOf(paramInt);
    synchronized (mImsUt.mLockObj)
    {
      Message localMessage = (Message)mImsUt.mPendingCmds.get(localInteger);
      mImsUt.mPendingCmds.remove(localInteger);
      return localMessage;
    }
  }

  public void updateCallBarringOption(String paramString, int paramInt, boolean paramBoolean, Message paramMessage, String[] paramArrayOfString)
  {
    int i = 0;
    Object localObject1 = mImsUt.mLockObj;
    int j = -1;
    for (;;)
    {
      try
      {
        localObject2 = new StringBuilder();
//        ((StringBuilder)localObject2).<init>();
        log("updateCallBarringOption:isHisiPlateform i " + isHisiPlateform);
        if (!isHisiPlateform)
        {
          if (miUt == null)
          {
            loge("The device is not Hisi plateform,but miUt is null");
            return;
          }
          try
          {
            paramInt = miUt.updateCallBarringOption(paramString, paramInt, paramBoolean, paramArrayOfString);
            if (paramInt >= 0) {
              break;
            }
            paramArrayOfString = mImsUt;
            paramString = new ImsReasonInfo();
//            paramString.<init>(802, 0);
            paramArrayOfString.sendFailureReport(paramMessage, paramString);
            return;
          }
          catch (RemoteException paramString)
          {
            paramString.printStackTrace();
            paramString = mImsUt;
            paramArrayOfString = new ImsReasonInfo();
//            paramArrayOfString.<init>(802, 0);
            paramString.sendFailureReport(paramMessage, paramArrayOfString);
            paramInt = j;
            continue;
          }
        }
        localParcel = Parcel.obtain();
      }
      finally {}
      Parcel localParcel;
      Object localObject2 = Parcel.obtain();
      IBinder localIBinder = ServiceManager.getService("ims_ut");
      if (localIBinder != null) {}
      try
      {
        localParcel.writeInterfaceToken("com.android.ims.internal.IImsUt");
        localParcel.writeInt(mPhoneId);
        localParcel.writeString(paramString);
        localParcel.writeInt(paramInt);
        paramInt = i;
        if (paramBoolean) {
          paramInt = 1;
        }
        localParcel.writeInt(paramInt);
        localParcel.writeStringArray(paramArrayOfString);
        localIBinder.transact(2004, localParcel, (Parcel)localObject2, 0);
        ((Parcel)localObject2).readException();
        for (paramInt = ((Parcel)localObject2).readInt();; paramInt = j)
        {
          ((Parcel)localObject2).recycle();
          localParcel.recycle();
          break;
          log("updateCallBarringOption - can't get ims_ut service");
        }
      }
      catch (RemoteException paramString)
      {
        paramString.printStackTrace();
        paramString = mImsUt;
        paramArrayOfString = new ImsReasonInfo();
//        paramArrayOfString.<init>(802, 0);
        paramString.sendFailureReport(paramMessage, paramArrayOfString);
        ((Parcel)localObject2).recycle();
        localParcel.recycle();
        paramInt = j;
      }
      finally
      {
        ((Parcel)localObject2).recycle();
        localParcel.recycle();
      }
    }
    mImsUt.mPendingCmds.put(Integer.valueOf(paramInt), paramMessage);
  }

  public void updateCallForwardUncondTimer(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, String paramString, Message paramMessage)
  {
    log("updateCallForwardUncondTimer :: , action=" + paramInt5 + ", condition=" + paramInt6 + ", startHour=" + paramInt1 + ", startMinute=" + paramInt2 + ", endHour=" + paramInt3 + ", endMinute=" + paramInt4);
    Object localObject1 = mImsUt.mLockObj;
    int i = -1;
    for (;;)
    {
      Object localObject2;
      try
      {
        localObject2 = StringBuilder();
//        ((StringBuilder)localObject2).<init>();
        log("updateCallForwardUncondTimer:isHisiPlateform i " + isHisiPlateform);
        if (!isHisiPlateform)
        {
          if (miUt == null)
          {
            loge("The device is not Hisi plateform,but miUt is null");
            return;
          }
          try
          {
            paramInt1 = miUt.updateCallForwardUncondTimer(paramInt1, paramInt2, paramInt3, paramInt4, paramInt5, paramInt6, paramString);
            if (paramInt1 >= 0) {
              break;
            }
            paramString = mImsUt;
            localObject2 = new ImsReasonInfo();
//            ((ImsReasonInfo)localObject2).<init>(802, 0);
            paramString.sendFailureReport(paramMessage, (ImsReasonInfo)localObject2);
            return;
          }
          catch (RemoteException paramString)
          {
            paramString.printStackTrace();
            paramString = mImsUt;
            localObject2 = new ImsReasonInfo();
//            ((ImsReasonInfo)localObject2).<init>(802, 0);
            paramString.sendFailureReport(paramMessage, (ImsReasonInfo)localObject2);
            paramInt1 = i;
            continue;
          }
        }
        localObject2 = Parcel.obtain();
      }
      finally {}
      Parcel localParcel = Parcel.obtain();
      Object localObject3 = ServiceManager.getService("ims_ut");
      if (localObject3 != null) {}
      try
      {
        ((Parcel)localObject2).writeInterfaceToken("com.android.ims.internal.IImsUt");
        ((Parcel)localObject2).writeInt(mPhoneId);
        ((Parcel)localObject2).writeInt(paramInt1);
        ((Parcel)localObject2).writeInt(paramInt2);
        ((Parcel)localObject2).writeInt(paramInt3);
        ((Parcel)localObject2).writeInt(paramInt4);
        ((Parcel)localObject2).writeInt(paramInt5);
        ((Parcel)localObject2).writeInt(paramInt6);
        ((Parcel)localObject2).writeString(paramString);
        ((IBinder)localObject3).transact(2003, (Parcel)localObject2, localParcel, 0);
        localParcel.readException();
        for (paramInt1 = localParcel.readInt();; paramInt1 = i)
        {
          localParcel.recycle();
          ((Parcel)localObject2).recycle();
          break;
          log("updateCallForwardUncondTimer - can't get ims_ut service");
        }
      }
      catch (RemoteException paramString)
      {
        paramString.printStackTrace();
        paramString = mImsUt;
        localObject3 = new ImsReasonInfo();
//        ((ImsReasonInfo)localObject3).<init>(802, 0);
        paramString.sendFailureReport(paramMessage, (ImsReasonInfo)localObject3);
        localParcel.recycle();
        ((Parcel)localObject2).recycle();
        paramInt1 = i;
      }
      finally
      {
        localParcel.recycle();
        ((Parcel)localObject2).recycle();
      }
    }
    mImsUt.mPendingCmds.put(Integer.valueOf(paramInt1), paramMessage);
  }
}
