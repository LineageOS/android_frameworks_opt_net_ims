package com.android.ims;

import android.os.Message;

public abstract class AbstractImsUt
  implements AbstractImsUtInterface
{
  protected ImsUtReference mReference = null;

  public boolean isSupportCFT()
  {
    return mReference.isSupportCFT();
  }

  public boolean isUtEnable()
  {
    return mReference.isUtEnable();
  }

  public Message popUtMessage(int paramInt)
  {
    return mReference.popUtMessage(paramInt);
  }

  public void updateCallBarringOption(String paramString, int paramInt, boolean paramBoolean, Message paramMessage, String[] paramArrayOfString)
  {
    mReference.updateCallBarringOption(paramString, paramInt, paramBoolean, paramMessage, paramArrayOfString);
  }

  public void updateCallForwardUncondTimer(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, String paramString, Message paramMessage)
  {
    mReference.updateCallForwardUncondTimer(paramInt1, paramInt2, paramInt3, paramInt4, paramInt5, paramInt6, paramString, paramMessage);
  }

  public static abstract interface ImsUtReference
  {
    public abstract boolean isSupportCFT();

    public abstract boolean isUtEnable();

    public abstract Message popUtMessage(int paramInt);

    public abstract void updateCallBarringOption(String paramString, int paramInt, boolean paramBoolean, Message paramMessage, String[] paramArrayOfString);

    public abstract void updateCallForwardUncondTimer(int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, String paramString, Message paramMessage);
  }
}
