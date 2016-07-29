package org.gsma.joyn.chat;

import org.gsma.joyn.JoynServiceException;
import org.gsma.joyn.Logger;

public class PublicAccountChat extends Chat {
    /**
     * Constructor
     *
     * @param chatIntf Chat interface
     */
    PublicAccountChat(IChat chatIntf) {
        super(chatIntf);
    }

    /**
     * Sends a large mode public account message
     *
     * @param message Message info
     * @return Unique message ID or null in case of error
     * @throws JoynServiceException
     */
   public String sendPublicAccountMessageByLargeMode(String message) throws JoynServiceException {

        Logger.i(TAG, "PAM sendPublicAccountMessageByLargeMode entry " + message);
        try {
            return chatInf.sendPublicAccountMessageByLargeMode(message);
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
   public String sendPublicAccountMessageByPagerMode(String message) throws JoynServiceException {

        Logger.i(TAG, "PAM sendPublicAccountMessageByPagerMode entry " + message);
        try {
            String extraParams[] = {"public" };
          return chatInf.sendMessageByPagerMode(message, false, true, false, false, null);
        } catch (Exception e) {
            throw new JoynServiceException(e.getMessage());
        }
    }
}
