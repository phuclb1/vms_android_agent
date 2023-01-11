package com.subzero.usbtest.streamlib;

import android.content.Context;
import android.media.MediaCodec;

import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OpenGlView;

import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 *
 * Created by pedro on 25/01/17.
 */

public class RtmpUSB2 extends USBBase2 {

    private SrsFlvMuxer srsFlvMuxer;

    public RtmpUSB2(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
        super(openGlView);
        srsFlvMuxer = new SrsFlvMuxer(connectChecker);
    }

    public RtmpUSB2(LightOpenGlView lightOpenGlView, ConnectCheckerRtmp connectChecker) {
        super(lightOpenGlView);
        srsFlvMuxer = new SrsFlvMuxer(connectChecker);
    }

    public RtmpUSB2(Context context, ConnectCheckerRtmp connectChecker) {
        super(context);
        srsFlvMuxer = new SrsFlvMuxer(connectChecker);
    }

    /**
     * H264 profile.
     *
     * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
     */
    public void setProfileIop(byte profileIop) {
        srsFlvMuxer.setProfileIop(profileIop);
    }

    @Override
    public void setAuthorization(String user, String password) {
        srsFlvMuxer.setAuthorization(user, password);
    }

    @Override
    protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
        srsFlvMuxer.setIsStereo(isStereo);
        srsFlvMuxer.setSampleRate(sampleRate);
    }

    @Override
    protected void startStreamRtp(String url) {
        if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
            srsFlvMuxer.setVideoResolution(videoEncoder.getHeight(), videoEncoder.getWidth());
        } else {
            srsFlvMuxer.setVideoResolution(videoEncoder.getWidth(), videoEncoder.getHeight());
        }
        srsFlvMuxer.start(url);
    }

    @Override
    protected void stopStreamRtp() {
        srsFlvMuxer.stop();
    }

    @Override
    public boolean shouldRetry(String reason) {
        return false;
    }

    @Override
    public void setReTries(int reTries) {

    }

    @Override
    protected void reConnect(long delay) {

    }

    @Override
    public void resizeCache(int newSize) throws RuntimeException {

    }

    @Override
    public int getCacheSize() {
        return 0;
    }

    @Override
    public long getSentAudioFrames() {
        return 0;
    }

    @Override
    public long getSentVideoFrames() {
        return 0;
    }

    @Override
    public long getDroppedAudioFrames() {
        return 0;
    }

    @Override
    public long getDroppedVideoFrames() {
        return 0;
    }

    @Override
    public void resetSentAudioFrames() {

    }

    @Override
    public void resetSentVideoFrames() {

    }

    @Override
    public void resetDroppedAudioFrames() {

    }

    @Override
    public void resetDroppedVideoFrames() {

    }

//    public void setNumRetriesConnect(Integer num){
//        if(num > 0){
//            srsFlvMuxer.setReTries(num);
//        }
//    }
//
//    public boolean reconnectRtp(String reason, final long delayMilis){
//        boolean shouldRetry = srsFlvMuxer.shouldRetry(reason);
//        if(shouldRetry){
//            srsFlvMuxer.reConnect(delayMilis);
//        }
//        return shouldRetry;
//    }
//
//    public void reconnectRtp(final long delayMilis) {
//        srsFlvMuxer.reConnect(delayMilis);
//    }

    @Override
    protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        srsFlvMuxer.sendAudio(aacBuffer, info);
    }

    @Override
    protected void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        srsFlvMuxer.setSpsPPs(sps, pps);
    }

    @Override
    protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        srsFlvMuxer.sendVideo(h264Buffer, info);
    }

    @Override
    public void setLogs(boolean enable) {

    }

}

