package com.subzero.usbtest.streamlib;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.pedro.encoder.Frame;
import com.pedro.rtmp.flv.video.ProfileIop;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 *
 * Created by pedro on 25/01/17.
 */

public class RtmpUSB extends USBBase {

    private RtmpClient rtmpClient;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public RtmpUSB(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
        super(openGlView);
        rtmpClient = new RtmpClient(connectChecker);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public RtmpUSB(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
        super(lightOpenGlView);
        rtmpClient = new RtmpClient(connectChecker);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public RtmpUSB(Context context, ConnectCheckerRtmp connectChecker) {
        super(context);
        rtmpClient = new RtmpClient(connectChecker);
    }

    /**
     * H264 profile.
     *
     * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
     */
    public void setProfileIop(ProfileIop profileIop) {
        rtmpClient.setProfileIop(profileIop);
    }

    @Override
    public void setAuthorization(String user, String password) {
        rtmpClient.setAuthorization(user, password);
    }

    @Override
    protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
        rtmpClient.setAudioInfo(sampleRate, isStereo);
    }

    @Override
    protected void startStreamRtp(String url) {
        if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
            rtmpClient.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
        } else {
            rtmpClient.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
        }
        rtmpClient.connect(url);
    }

    @Override
    protected void stopStreamRtp() {
        rtmpClient.disconnect();
    }

    public void setNumRetriesConnect(Integer num){
        if(num > 0){
            rtmpClient.setReTries(num);
        }
    }

    public boolean reconnectRtp(String reason, final long delayMilis){
        boolean shouldRetry = rtmpClient.shouldRetry(reason);
        if(shouldRetry){
            rtmpClient.reConnect(delayMilis);
        }
        return shouldRetry;
    }

    @Override
    protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        rtmpClient.sendAudio(aacBuffer, info);
    }

    @Override
    protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        rtmpClient.setVideoInfo(sps, pps, vps);
    }

    @Override
    protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        rtmpClient.sendVideo(h264Buffer, info);
    }

    @Override
    public void inputPCMData(Frame frame) {

    }

    @Override
    public void inputYUVData(Frame frame) {

    }
}

