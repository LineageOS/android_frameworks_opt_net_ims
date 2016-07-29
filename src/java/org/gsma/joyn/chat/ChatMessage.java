/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/
package org.gsma.joyn.chat;

import java.util.Date;

import org.gsma.joyn.Logger;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Chat message
 *
 * @author Jean-Marc AUFFRET
 */
public class ChatMessage implements Parcelable {
    /**
     * MIME type
     */
    public final static String MIME_TYPE = "text/plain";

    public static final String TAG = "TAPI-ChatMessage";
    /**
     * Unique message Id
     */
    private String id;

    /**
     * Contact who has sent the message
     */
    private String contact;

    /**
     * Message content
     */
    private String message;

    /**
     * Receipt date of the message
     */
    private Date receiptAt;

    /**
     * Display Name
     */
    private String displayName;

    /**
     * Flag indicating is a displayed report is requested
     */
    private boolean displayedReportRequested = false;


    /**
     * burn message
     */
    private boolean isBurnMessage = false;

    private boolean isPublicMessage = false;

    private boolean isCloudMessage = false;

    private boolean isEmoticonMessage = false;


    /**
     * Constructor for outgoing message
     *
     * @param messageId Message Id
     * @param contact Contact
     * @param message Message content
     * @param receiptAt Receipt date
     * @param displayedReportRequested Flag indicating if a displayed report is requested
     * @hide
     */
    public ChatMessage(String messageId, String remote, String message, Date receiptAt, boolean displayedReportRequested, String displayName) {
        Logger.i(TAG, "ChatMessage entry" + "messageId=" + messageId + " remote=" + remote + " message=" + message +
                " receiptAt=" + receiptAt + " displayedReportRequested=" + displayedReportRequested);
        Logger.i(TAG, "ABCG ChatMessage entry" + "displayname=" + displayName);
        this.id = messageId;
        this.contact = remote;
        this.message = message;
        this.displayedReportRequested = displayedReportRequested;
        this.receiptAt = receiptAt;
        this.displayName = displayName;
    }

    /**
     * Constructor
     *
     * @param source Parcelable source
     * @hide
     */
    public ChatMessage(Parcel source) {
        this.id = source.readString();
        this.contact = source.readString();
        this.message = source.readString();
        this.receiptAt = new Date(source.readLong());
        this.displayedReportRequested = source.readInt() != 0;
        this.displayName = source.readString();
        this.isBurnMessage = source.readByte() != 0;
        this.isPublicMessage = source.readByte() != 0;
        this.isCloudMessage = source.readByte() != 0;
        this.isEmoticonMessage = source.readByte() != 0;
    }

    /**
     * Describe the kinds of special objects contained in this Parcelable's
     * marshalled representation
     *
     * @return Integer
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * Write parcelable object
     *
     * @param dest The Parcel in which the object should be written
     * @param flags Additional flags about how the object should be written
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(contact);
        dest.writeString(message);
        dest.writeLong(receiptAt.getTime());
        dest.writeInt(displayedReportRequested ? 1 : 0);
        dest.writeString(displayName);
        dest.writeByte((byte) (isBurnMessage ? 1 : 0));
        dest.writeByte((byte) (isPublicMessage ? 1 : 0));
        dest.writeByte((byte) (isCloudMessage ? 1 : 0));
        dest.writeByte((byte) (isEmoticonMessage ? 1 : 0));
    }

    /**
     * Parcelable creator
     *
     * @hide
     */
    public static final Parcelable.Creator<ChatMessage> CREATOR
            = new Parcelable.Creator<ChatMessage>() {
        public ChatMessage createFromParcel(Parcel source) {
            return new ChatMessage(source);
        }

        public ChatMessage[] newArray(int size) {
            return new ChatMessage[size];
        }
    };

    /**
     * Returns the message ID
     *
     * @return ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the contact
     *
     * @return Contact
     */
    public String getContact() {
        return contact;
    }

    /**
     * Returns the message content
     *
     * @return String
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the display name
     *
     * @return String
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the receipt date of chat message
     *
     * @return Date
     */
    public Date getReceiptDate() {
        return receiptAt;
    }

    /**
     * Is displayed delivery report requested
     *
     * @return Returns true if requested else returns false
     */
    public boolean isDisplayedReportRequested() {
        return displayedReportRequested;
    }

    /**
     * Is the chat message of burn type
     *
     * @return Returns true if chat message is of burn type else returns false
     */
    public boolean isBurnMessage() {
        return isBurnMessage;
    }

     /**
     * Set the message type of chat message as burn message
     *
     * @param burnFlag Flag indicating whether message is burn message type
     */
    public void setBurnMessage(boolean burnFlag) {
        isBurnMessage = burnFlag;
    }

    public boolean isCloudMessage() {
        return isCloudMessage;
    }

    public void setCloudMessage(boolean burnFlag) {
        isCloudMessage = burnFlag;
    }

    public boolean isEmoticonMessage() {
        return isEmoticonMessage;
    }

    public void setEmoticonMessage(boolean burnFlag) {
        isEmoticonMessage = burnFlag;
    }

    /**
     * Is the chat message of public chat type
     *
     * @return Returns true if chat message is of public chat type else returns false
     */
    public boolean isPublicMessage() {
        return isPublicMessage;
    }

    /**
     * Set the message type of chat message as public chat
     *
     * @param publicFlag Flag indicating whether message is public chat type
     */
    public void setPublicMessage(boolean publicFlag) {
        isPublicMessage = publicFlag;
    }
}
