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

import java.util.ArrayList;
import java.util.HashSet;

import org.gsma.joyn.JoynServiceException;

import org.gsma.joyn.Logger;

/**
 * Chat
 *
 * @author Jean-Marc AUFFRET
 */
public class Chat {

    /**
     * Direction of the group chat
     */
    public static class MessageState {
        /**
         * Message being sent
         */
        public static final int SENDING = 0;

        /**
         * Message sent
         */
        public static final int SENT = 1;

        /**
         * Message delivered to remote
         */
        public static final int DELIVERED = 2;

        /**
         * Message sending failed
         */
        public static final int FAILED = 3;
    }

    /**
     * Direction of the group chat
     */
    public static class ErrorCodes {
        /**
         * Message being sent
         */
        public static final int TIMEOUT = 1;

        /**
         * Message sent
         */
        public static final int UNKNOWN = 2;

        /**
         * Message delivered to remote
         */
        public static final int INTERNAL_EROR = 3;

        /**
         * Message sending failed
         */
        public static final int OUT_OF_SIZE = 4;
    }

    /**
     * Chat interface
     */
    protected IChat chatInf;

    public static final String TAG = "TAPI-Chat";

    /**
     * Constructor
     *
     * @param chatIntf Chat interface
     */
    Chat(IChat chatIntf) {
        this.chatInf = chatIntf;
    }

    /**
     * Returns the remote contact
     *
     * @return Contact
     * @throws JoynServiceException
     */
    public String getRemoteContact() throws JoynServiceException {
        Logger.i(TAG, "getRemoteContact entry");
        try {
            return chatInf.getRemoteContact();
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Sends a chat message
     *
     * @param message Message
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
    public String sendMessage(String message) throws JoynServiceException {
        Logger.i(TAG, "ABC sendMessage entry " + message);
        try {
            return chatInf.sendMessage(message);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Sends a geoloc message
     *
     * @param geoloc Geoloc info
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
    public String sendGeoloc(Geoloc geoloc) throws JoynServiceException {

        Logger.i(TAG, "sendGeoloc entry " + geoloc);
        try {
            return chatInf.sendGeoloc(geoloc);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Sends a large mode message
     *
     * @param message Message info
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
   public String sendMessageByLargeMode(String message) throws JoynServiceException {

        Logger.i(TAG, "sendMessageByLargeMode entry " + message);
        try {
            return chatInf.sendMessageByLargeMode(message);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

  /**
    * Sends a cloud large mode message
    *
    * @param message Message info
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
    */
  public String sendCloudMessageByLargeMode(String message) throws JoynServiceException {

    Logger.i(TAG, "sendCloudMessageByLargeMode entry " + message);
        try {
            return chatInf.sendCloudMessageByLargeMode(message);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
   }

  /**
     * Sends a pager mode message
     *
     * @param messageId Message id
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
  public void sendSpamMessageByPagerMode(String contact, String messageId)  throws JoynServiceException {

        Logger.i(TAG, "sendSpamMessageByPagerMode entry " + messageId);
        try {
            chatInf.sendSpamMessageByPagerMode(contact, messageId);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

   /**
    * Sends a pager mode message for spam report
    *
     * @param message Message info
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
   public String sendMessageByPagerMode(String message) throws JoynServiceException {

        Logger.i(TAG, "sendMessageByPagerMode entry " + message);
        try {
            return chatInf.sendMessageByPagerMode(message, false, false, false, false, null);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

   /**
    * Sends a pager mode message
    *
    * @param message Message info
    * @return Unique message ID or null in case of error
    * @throws JoynServiceException
    */
  public String sendOnetoMultiMessageByPagerMode(String message, HashSet<String> participants) throws JoynServiceException {

       Logger.i(TAG, "sendMessageByPagerMode entry " + message);
       try {
           return chatInf.sendMessageByPagerMode(message, false, false, true, false, new ArrayList<String>(participants));
       } catch (Exception e) {
           throw new JoynServiceException(e.getMessage());
       }
   }

  /**
    * Sends a pager mode emoticon message
    * 
    * @param message Message info
    * @return Unique message ID or null in case of error
    * @throws JoynServiceException
    */
  public String sendOnetoMultiEmoticonsMessageByPagerMode(String message, HashSet<String> participants) throws JoynServiceException {
       
       Logger.i(TAG, "sendOnetoMultiEmoticonsMessageByPagerMode entry " + message + "Participants: " + participants);
       try {
           return chatInf.sendOnetoMultiEmoticonsMessage(message,new ArrayList<String>(participants));
       } catch(Exception e) {
           throw new JoynServiceException(e.getMessage());
       }       
   }
   
   
  /**
   * Sends a pager mode message
   *
   * @param message Message info
   * @return Unique message ID or null in case of error
   * @throws JoynServiceException
   */
 public String sendOnetoMultiMessage(String message, HashSet<String> participants) throws JoynServiceException {

      try {
          return chatInf.sendOnetoMultiMessage(message, new ArrayList<String>(participants));
      } catch (Exception e) {
          throw new JoynServiceException(e.getMessage());
      }
  }


 /**
  * Sends a pager mode message
  *
  * @param message Message info
  * @return Unique message ID or null in case of error
  * @throws JoynServiceException
  */
public String sendEmoticonShopMessage(String message) throws JoynServiceException {

     try {
         return chatInf.sendEmoticonShopMessage(message);
     } catch (Exception e) {
         throw new JoynServiceException(e.getMessage());
     }
 }

public String sendCloudMessage(String message) throws JoynServiceException {

     try {
         return chatInf.sendCloudMessage(message);
     } catch (Exception e) {
         throw new JoynServiceException(e.getMessage());
     }
 }




  public String sendBurnMessage(String message)throws JoynServiceException {
      Logger.i(TAG, "sendBurnMessage entry " + message);
        try {
        //  return chatInf.sendGeoloc(geoloc);
        return null;
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
  }



  public String sendPagerModeBurnMessage(String message)throws JoynServiceException {
      Logger.i(TAG, "sendPagerModeBurnMessage entry " + message);
        try {
            return chatInf.sendPagerModeBurnMessage(message);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
  }

  /**
   * Sends a burn message by large message mode
   *
   * @param message Message info
   * @return Unique message ID or null in case of error
   * @throws JoynServiceException
   */
  public String sendLargeModeBurnMessage(String message)throws JoynServiceException {
      Logger.i(TAG, "sendLargeModeBurnMessage entry " + message);
        try {
        return chatInf.sendLargeModeBurnMessage(message);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
  }

    /**
     * Sends a displayed delivery report for a given message ID
     *
     * @param msgId Message ID
     * @throws JoynServiceException
     */
    public void sendDisplayedDeliveryReport(String msgId) throws JoynServiceException {
        Logger.i(TAG, "sendDisplayedDeliveryReport entry " + msgId);
        try {
            chatInf.sendDisplayedDeliveryReport(msgId);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Sends a burn report for a given burn message ID
     *
     * @param msgId Message ID
     * @throws JoynServiceException
     */
    public void sendBurnDeliveryReport(String msgId) throws JoynServiceException {
        Logger.i(TAG, "sendBurnDeliveryReport entry " + msgId);
        try {
            chatInf.sendBurnDeliveryReport(msgId);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Sends an Is-composing event. The status is set to true when
     * typing a message, else it is set to false.
     *
     * @param status Is-composing status
     * @throws JoynServiceException
     */
    public void sendIsComposingEvent(boolean status) throws JoynServiceException {
        Logger.i(TAG, "sendIsComposingEvent entry " + status);
        try {
            chatInf.sendIsComposingEvent(status);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     *
     * @param msgId message Id of message
     * @return state of the message
     * @throws JoynServiceException
     */
    public int resendMessage(String msgId) throws JoynServiceException {
        Logger.i(TAG, "resendMessage msgId " + msgId);
        try {
            return chatInf.resendMessage(msgId);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     *
     * @param msgId message Id of message
     * @throws JoynServiceException
     */
    public int reSendMultiMessageByPagerMode(String msgId) throws JoynServiceException {
        Logger.i(TAG, "reSendMultiMessageByPagerMode msgId " + msgId);
        try {
            return chatInf.reSendMultiMessageByPagerMode(msgId);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     *
     * @param msgId message Id of message
     * @return state of the message
     * @throws JoynServiceException
     */
    public int getState(String msgId) throws JoynServiceException {
        Logger.i(TAG, "getState MessageId= " + msgId);
        try {
            return 0;
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Adds a listener on chat events
     *
     * @param listener Chat event listener
     * @throws JoynServiceException
     */
    public void addEventListener(ChatListener listener) throws JoynServiceException {
        Logger.i(TAG, "addEventListener entry " + listener);
        try {
            chatInf.addEventListener(listener);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Removes a listener on chat events
     *
     * @param listener Chat event listener
     * @throws JoynServiceException
     */
    public void removeEventListener(ChatListener listener) throws JoynServiceException {
        Logger.i(TAG, "removeEventListener entry " + listener);
        try {
            chatInf.removeEventListener(listener);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Adds a listener on spam events
     *
     * @param listener Spam event listener
     * @throws JoynServiceException
     */
    public void addSpamReportListener(SpamReportListener listener) throws JoynServiceException {
        Logger.i(TAG, "addSpamReportListener entry " + listener);
        try {
            chatInf.addSpamReportListener(listener);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }

    /**
     * Removes a listener on spam events
     *
     * @param listener Spam event listener
     * @throws JoynServiceException
     */
    public void removeSpamReportListener(SpamReportListener listener) throws JoynServiceException {
        Logger.i(TAG, "removeSpamReportListener entry " + listener);
        try {
            chatInf.removeSpamReportListener(listener);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }
}
