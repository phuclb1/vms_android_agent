package com.subzero.usbtest.streamlib;

import android.content.Context;
import android.media.MediaCodec;

import androidx.annotation.Nullable;

import com.pedro.encoder.Frame;
import com.pedro.rtmp.rtmp.RtmpClient;
import com.pedro.rtmp.utils.ConnectCheckerRtmp;
import com.pedro.rtplibrary.view.OpenGlView;

import java.nio.ByteBuffer;

public class RtmpUSB2 extends USBBase2 {

    private final RtmpClient rtmpClient;

    public RtmpUSB2(OpenGlView openGlView, ConnectCheckerRtmp connectChecker) {
        super(openGlView);
        rtmpClient = new RtmpClient(connectChecker);
    }

    public RtmpUSB2(Context context, ConnectCheckerRtmp connectChecker) {
        super(context);
        rtmpClient = new RtmpClient(connectChecker);
    }

//    /**
//     * H264 profile.
//     *
//     * @param profileIop Could be ProfileIop.BASELINE or ProfileIop.CONSTRAINED
//     */
//    public void setProfileIop(ProfileIop profileIop) {
//        rtmpClient.setProfileIop(profileIop);
//    }

    @Override
    public void resizeCache(int newSize) throws RuntimeException {
        rtmpClient.resizeCache(newSize);
    }

    @Override
    public int getCacheSize() {
        return rtmpClient.getCacheSize();
    }

    @Override
    public long getSentAudioFrames() {
        return rtmpClient.getSentAudioFrames();
    }

    @Override
    public long getSentVideoFrames() {
        return rtmpClient.getSentVideoFrames();
    }

    @Override
    public long getDroppedAudioFrames() {
        return rtmpClient.getDroppedAudioFrames();
    }

    @Override
    public long getDroppedVideoFrames() {
        return rtmpClient.getDroppedVideoFrames();
    }

    @Override
    public void resetSentAudioFrames() {
        rtmpClient.resetSentAudioFrames();
    }

    @Override
    public void resetSentVideoFrames() {
        rtmpClient.resetSentVideoFrames();
    }

    @Override
    public void resetDroppedAudioFrames() {
        rtmpClient.resetDroppedAudioFrames();
    }

    @Override
    public void resetDroppedVideoFrames() {
        rtmpClient.resetDroppedVideoFrames();
    }

    @Override
    public void setAuthorization(String user, String password) {
        rtmpClient.setAuthorization(user, password);
    }

    public void setWriteChunkSize(int chunkSize) {
        if (!isStreaming()) {
            rtmpClient.setWriteChunkSize(chunkSize);
        }
    }

    public void forceAkamaiTs(boolean enabled) {
        rtmpClient.forceAkamaiTs(enabled);
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
        rtmpClient.setFps(videoEncoder.getFps());
        rtmpClient.setOnlyVideo(!audioInitialized);
        rtmpClient.connect(url);
    }

    @Override
    protected void stopStreamRtp() {
        rtmpClient.disconnect();
    }

    @Override
    protected boolean shouldRetry(String reason) {
        return rtmpClient.shouldRetry(reason);
    }

    @Override
    public void setReTries(int reTries) {
        rtmpClient.setReTries(reTries);
    }

    @Override
    public void reConnect(long delay, @Nullable String backupUrl) {
        rtmpClient.reConnect(delay, backupUrl);
    }

    @Override
    public boolean hasCongestion() {
        return rtmpClient.hasCongestion();
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
    public void setLogs(boolean enable) {
        rtmpClient.setLogs(enable);
    }

    @Override
    public void setCheckServerAlive(boolean enable) {
        rtmpClient.setCheckServerAlive(enable);
    }

    @Override
    public void inputYUVData(Frame frame) {

    }

    @Override
    public void setAudioInit(boolean enable){
        audioInitialized = enable;
    }
}

