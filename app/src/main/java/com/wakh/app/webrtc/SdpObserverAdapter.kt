package com.wakh.app.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
