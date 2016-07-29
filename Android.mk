# Copyright 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/src/java
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src/java)


# MTK
LOCAL_SRC_FILES += \
    src/java/org/gsma/joyn/IJoynServiceRegistrationListener.aidl\
    src/java/org/gsma/joyn/capability/ICapabilitiesListener.aidl\
    src/java/org/gsma/joyn/capability/ICapabilityService.aidl\
    src/java/org/gsma/joyn/chat/IChat.aidl\
    src/java/org/gsma/joyn/chat/IChatListener.aidl\
    src/java/org/gsma/joyn/chat/IChatService.aidl\
    src/java/org/gsma/joyn/chat/IGroupChatListener.aidl\
    src/java/org/gsma/joyn/chat/INewChatListener.aidl\
    src/java/org/gsma/joyn/chat/IGroupChat.aidl\
    src/java/org/gsma/joyn/chat/IGroupChatSyncingListener.aidl\
    src/java/org/gsma/joyn/chat/ISpamReportListener.aidl\
    src/java/org/gsma/joyn/gsh/IGeolocSharingListener.aidl\
    src/java/org/gsma/joyn/gsh/INewGeolocSharingListener.aidl\
    src/java/org/gsma/joyn/gsh/IGeolocSharing.aidl\
    src/java/org/gsma/joyn/gsh/IGeolocSharingService.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCall.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallPlayer.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallRenderer.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallListener.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallPlayerListener.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallRendererListener.aidl\
    src/java/org/gsma/joyn/ipcall/IIPCallService.aidl\
    src/java/org/gsma/joyn/ipcall/INewIPCallListener.aidl\
    src/java/org/gsma/joyn/ish/IImageSharing.aidl\
    src/java/org/gsma/joyn/ish/IImageSharingListener.aidl\
    src/java/org/gsma/joyn/ish/IImageSharingService.aidl\
    src/java/org/gsma/joyn/ish/INewImageSharingListener.aidl\
    src/java/org/gsma/joyn/vsh/INewVideoSharingListener.aidl\
    src/java/org/gsma/joyn/vsh/IVideoSharingListener.aidl\
    src/java/org/gsma/joyn/vsh/IVideoPlayer.aidl\
    src/java/org/gsma/joyn/vsh/IVideoPlayerListener.aidl\
    src/java/org/gsma/joyn/vsh/IVideoRenderer.aidl\
    src/java/org/gsma/joyn/vsh/IVideoRendererListener.aidl\
    src/java/org/gsma/joyn/vsh/IVideoSharing.aidl\
    src/java/org/gsma/joyn/vsh/IVideoSharingService.aidl\
    src/java/org/gsma/joyn/session/IMultimediaSession.aidl\
    src/java/org/gsma/joyn/session/IMultimediaSessionListener.aidl\
    src/java/org/gsma/joyn/session/IMultimediaSessionService.aidl\
    src/java/org/gsma/joyn/ft/IFileTransfer.aidl\
    src/java/org/gsma/joyn/ft/IFileTransferService.aidl\
    src/java/org/gsma/joyn/ft/IFileTransferListener.aidl\
    src/java/org/gsma/joyn/ft/INewFileTransferListener.aidl\
    src/java/org/gsma/joyn/ft/IFileSpamReportListener.aidl\
    src/java/org/gsma/joyn/contacts/IContactsService.aidl\
    src/java/org/gsma/joyn/ICoreServiceWrapper.aidl \


#LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := ims-common

include $(BUILD_JAVA_LIBRARY)

# build MTK ImsService
include $(call all-makefiles-under,$(LOCAL_PATH))
