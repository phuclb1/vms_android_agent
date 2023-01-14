package com.subzero.usbtest.streamlib;

import android.content.Context;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.base.recording.BaseRecordController;
import com.pedro.rtplibrary.base.recording.RecordController;
import com.pedro.rtplibrary.util.AndroidMuxerRecordController;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;
import com.serenegiant.usb.UVCCamera;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class USBBase2
        implements GetAacData, GetCameraData, GetVideoData, GetMicrophoneData {

    private static final String TAG = "USBBase2";

    private Context context;
    protected VideoEncoder videoEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;
    private boolean streaming = false;
//    private SurfaceView surfaceView;
//    private TextureView textureView;
    private GlInterface glInterface;
    protected boolean audioInitialized = false;
    private boolean videoEnabled = true;
    private boolean onPreview = false;
    private boolean isBackground = false;
    protected BaseRecordController recordController;
    private int previewWidth, previewHeight;
    private final FpsListener fpsListener = new FpsListener();

    public USBBase2(OpenGlView openGlView) {
        context = openGlView.getContext();
        this.glInterface = openGlView;
        this.glInterface.init();
        init();
    }

    public USBBase2(Context context) {
        this.context = context;
        glInterface = new OffScreenGlThread(context);
        glInterface.init();
        isBackground = true;
        init();
    }

    private void init() {
        videoEncoder = new VideoEncoder(this);
        microphoneManager = new MicrophoneManager(this);
//        audioEncoder = new AudioEncoder(this);
        setMicrophoneMode(MicrophoneMode.ASYNC);
        recordController = new AndroidMuxerRecordController();
    }

    /**
     * Must be called before prepareAudio.
     *
     * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
     * SYNC using same thread. This mode could solve choppy audio or AudioEncoder frame discarded.
     * ASYNC using other thread.
     */
    public void setMicrophoneMode(MicrophoneMode microphoneMode) {
        switch (microphoneMode) {
            case SYNC:
                microphoneManager = new MicrophoneManagerManual();
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
                audioEncoder.setTsModeBuffer(false);
                break;
            case ASYNC:
                microphoneManager = new MicrophoneManager(this);
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setTsModeBuffer(false);
                break;
            case BUFFER:
                microphoneManager = new MicrophoneManager(this);
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setTsModeBuffer(true);
                break;
        }
    }

    /**
     * Set an audio effect modifying microphone's PCM buffer.
     */
    public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
        microphoneManager.setCustomAudioEffect(customAudioEffect);
    }

    /**
     * @param callback get fps while record or stream
     */
    public void setFpsListener(FpsListener.Callback callback) {
        fpsListener.setCallback(callback);
    }

    public abstract void setAuthorization(String user, String password);


    /**
     * prepare Video
     */
    public boolean prepareVideo(UVCCamera uvcCamera, int width, int height, int fps, int bitrate,
                                int iFrameInterval, int rotation,
                                int avcProfile, int avcProfileLevel) {
        if (onPreview && glInterface != null && (width != previewWidth || height != previewHeight
                || fps != videoEncoder.getFps() || rotation != videoEncoder.getRotation())) {
            stopPreview(uvcCamera);
            onPreview = true;
        }
        boolean result = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
                iFrameInterval, FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
        return result;
    }

    public boolean prepareVideo(UVCCamera uvcCamera, int width, int height, int fps, int bitrate, int iFrameInterval,
                                int rotation) {
        return prepareVideo(uvcCamera, width, height, fps, bitrate, iFrameInterval, rotation, -1, -1);
    }

    public boolean prepareVideo(UVCCamera uvcCamera, int width, int height, int fps, int bitrate, int rotation) {
        return prepareVideo(uvcCamera, width, height, fps, bitrate, 2, rotation);
    }

    public boolean prepareVideo(UVCCamera uvcCamera, int width, int height, int bitrate) {
        int rotation = CameraHelper.getCameraOrientation(context);
        return prepareVideo(uvcCamera, width, height, 30, bitrate, 2, rotation);
    }

    public boolean prepareVideo(UVCCamera uvcCamera) {
        int rotation = CameraHelper.getCameraOrientation(context);
        return prepareVideo(uvcCamera, 640, 480, 30, 1200 * 1024, rotation);
    }


    /**
     * Prepare Audio
     */
    public boolean prepareAudio(int audioSource, int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        if (!microphoneManager.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
            return false;
        }
        prepareAudioRtp(isStereo, sampleRate);
        audioInitialized = audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
                microphoneManager.getMaxInputSize());
        return audioInitialized;
    }

    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        return prepareAudio(MediaRecorder.AudioSource.DEFAULT, bitrate, sampleRate, isStereo, echoCanceler,
                noiseSuppressor);
    }

    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
        return prepareAudio(bitrate, sampleRate, isStereo, false, false);
    }

    public boolean prepareAudio() {
        return prepareAudio(64 * 1024, 32000, true, false, false);
    }

    protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);




    /**
     * Set force
     */
    public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
        videoEncoder.setForce(forceVideo);
        audioEncoder.setForce(forceAudio);
    }


    /**
     * Start record
     */
    public void startRecord(UVCCamera uvcCamera, @NonNull String path, @Nullable RecordController.Listener listener)
            throws IOException {
        recordController.startRecord(path, listener);
        if (!streaming) {
            startEncoders(uvcCamera);
        } else if (videoEncoder.isRunning()) {
            requestKeyFrame();
//            resetVideoEncoder(uvcCamera);
        }
    }

    public void startRecord(UVCCamera uvcCamera, @NonNull final String path) throws IOException {
        startRecord(uvcCamera, path, null);
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    public void startRecord(UVCCamera uvcCamera, @NonNull final FileDescriptor fd,
                            @Nullable RecordController.Listener listener) throws IOException {
        recordController.startRecord(fd, listener);
        if (!streaming) {
            startEncoders(uvcCamera);
        } else if (videoEncoder.isRunning()) {
            requestKeyFrame();
        }
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    public void startRecord(UVCCamera uvcCamera, @NonNull final FileDescriptor fd) throws IOException {
        startRecord(uvcCamera, fd, null);
    }


    /**
     * Stop record
     */
    public void stopRecord(UVCCamera uvcCamera) {
        recordController.stopRecord();
        if (!streaming) stopStream(uvcCamera);
    }

    public void requestKeyFrame() {
        if (videoEncoder.isRunning()) {
            videoEncoder.requestKeyframe();
        }
    }


    /**
     * Replace View
     */
    public void replaceView(UVCCamera uvcCamera, Context context) {
        isBackground = true;
        replaceGlInterface(uvcCamera, new OffScreenGlThread(context));
    }

    public void replaceView(UVCCamera uvcCamera, OpenGlView openGlView) {
        isBackground = false;
        replaceGlInterface(uvcCamera, openGlView);
    }

    private void replaceGlInterface(final UVCCamera uvcCamera, GlInterface glInterface) {
        if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
            if (isStreaming() || isRecording() || isOnPreview()) {
                Point size = this.glInterface.getEncoderSize();
                uvcCamera.stopPreview();
                this.glInterface.removeMediaCodecSurface();
                this.glInterface.stop();
                this.glInterface = glInterface;
                this.glInterface.init();
                this.glInterface.setEncoderSize(size.x, size.y);
//                boolean isPortrait = CameraHelper.isPortrait(context);
//                if (!isPortrait) {
//                    this.glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
//                } else {
//                    this.glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
//                }
                this.glInterface.setRotation(videoEncoder.getRotation());
//                this.glInterface.setRotation(
//                        videoEncoder.getRotation() == 0 ? 270 : videoEncoder.getRotation() - 90);
                this.glInterface.start();
                if (isStreaming() || isRecording()) {
                    this.glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
                }
                uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
                uvcCamera.startPreview();
            } else {
                this.glInterface = glInterface;
                this.glInterface.init();
            }
        }
    }





    /**
     * Start camera preview
     */
    public void startPreview(final UVCCamera uvcCamera, int width, int height, int fps, int rotation) {
        if (!isStreaming() && !onPreview && !isBackground) {
            previewWidth = width;
            previewHeight = height;
            videoEncoder.setFps(fps);
            videoEncoder.setRotation(rotation);

            if (glInterface != null) {
//                boolean isPortrait = CameraHelper.isPortrait(context);
//                if (!isPortrait) {
//                    glInterface.setEncoderSize(height, width);
//                } else {
//                    glInterface.setEncoderSize(width, height);
//                }
                if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
                    glInterface.setEncoderSize(height, width);
                } else {
                    glInterface.setEncoderSize(width, height);
                }
//                glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
                glInterface.setRotation(rotation);
                glInterface.setFps(videoEncoder.getFps());
                glInterface.start();
                uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
                uvcCamera.startPreview();
            }
            onPreview = true;
        }
    }

    /**
     * Stop camera preview
     */
    public void stopPreview(UVCCamera uvcCamera) {
        if (!isStreaming() && !isRecording() && onPreview && !isBackground) {
            if (glInterface != null) {
                glInterface.stop();
            }
            uvcCamera.stopPreview();
            onPreview = false;
            previewWidth = 0;
            previewHeight = 0;
        }
    }



    /**
     * Start stream
     */
    public void startStream(UVCCamera uvcCamera, String url) {
        streaming = true;
        if (!recordController.isRunning()) {
            startEncoders(uvcCamera);
        } else {
//            resetVideoEncoder(uvcCamera);
            requestKeyFrame();
        }
        startStreamRtp(url);
        onPreview = true;
    }

    protected abstract void startStreamRtp(String url);

    /**
     * Stop Stream
     */
    protected abstract void stopStreamRtp();

    public void stopStream(UVCCamera uvcCamera) {
        if (streaming) {
            streaming = false;
            stopStreamRtp();
        }
        if (!recordController.isRecording()) {
            onPreview = !isBackground;
            if (audioInitialized) microphoneManager.stop();
            if (glInterface != null) {
                glInterface.removeMediaCodecSurface();
                if (glInterface instanceof OffScreenGlThread) {
                    glInterface.stop();
                    uvcCamera.stopPreview();
                    uvcCamera.close();
                }
            } else {
                if (isBackground) {
                    uvcCamera.close();
                    onPreview = false;
                } else {
                }
            }
            videoEncoder.stop();
            if (audioInitialized) audioEncoder.stop();
            recordController.resetFormats();
        }
    }


    /**
     * Encoder
     */
    private void startEncoders(UVCCamera uvcCamera) {
        videoEncoder.start();
        if (audioInitialized) audioEncoder.start();
        prepareGlView(uvcCamera);
        if (audioInitialized) microphoneManager.start();
        if (glInterface == null && videoEncoder.getWidth() != previewWidth
                || videoEncoder.getHeight() != previewHeight) {
            uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
            uvcCamera.startPreview();
        }
        onPreview = true;
    }

    private void prepareGlView(UVCCamera uvcCamera) {
        if (glInterface != null) {
            glInterface.setFps(videoEncoder.getFps());
            if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
                glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
            } else {
                glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
            }
            int rotation = videoEncoder.getRotation();
//            glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
            glInterface.setRotation(rotation);
            if (videoEncoder.getWidth() != previewWidth
                    || videoEncoder.getHeight() != previewHeight) {
                glInterface.start();
            }
            if (videoEncoder.getInputSurface() != null) {
                glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
            }

            uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
        }
    }


    /**
     * Retry connect
     */
    public boolean reTry(long delay, String reason, @Nullable String backupUrl) {
        boolean result = shouldRetry(reason);
        if (result) {
            requestKeyFrame();
            reConnect(delay, backupUrl);
        }
        return result;
    }

    public boolean reTry(long delay, String reason) {
        return reTry(delay, reason, null);
    }

    protected abstract boolean shouldRetry(String reason);

    public abstract void setReTries(int reTries);

    protected abstract void reConnect(long delay, @Nullable String backupUrl);





    //cache control
    public abstract boolean hasCongestion();

    public abstract void resizeCache(int newSize) throws RuntimeException;

    public abstract int getCacheSize();

    public abstract long getSentAudioFrames();

    public abstract long getSentVideoFrames();

    public abstract long getDroppedAudioFrames();

    public abstract long getDroppedVideoFrames();

    public abstract void resetSentAudioFrames();

    public abstract void resetSentVideoFrames();

    public abstract void resetDroppedAudioFrames();

    public abstract void resetDroppedVideoFrames();


    /**
     * Set Audio
     */
    public void setAudioMaxInputSize(int size) {
        microphoneManager.setMaxInputSize(size);
    }

    public void disableAudio() {
        microphoneManager.mute();
    }

    public void enableAudio() {
        microphoneManager.unMute();
    }

    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }


    /**
     * Get video camera state
     *
     * @return true if disabled, false if enabled
     */
    public int getBitrate() {
        return videoEncoder.getBitRate();
    }

    public int getResolutionValue() {
        return videoEncoder.getWidth() * videoEncoder.getHeight();
    }

    public int getStreamWidth() {
        return videoEncoder.getWidth();
    }

    public int getStreamHeight() {
        return videoEncoder.getHeight();
    }

    public GlInterface getGlInterface() {
        if (glInterface != null) {
            return glInterface;
        } else {
            throw new RuntimeException("You can't do it. You are not using Opengl");
        }
    }

    /**
     * Set video bitrate of H264 in kb while stream.
     * @param bitrate H264 in kb.
     */
    public void setVideoBitrateOnFly(int bitrate) {
        videoEncoder.setVideoBitrateOnFly(bitrate);
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
     * could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    public void setLimitFPSOnFly(int fps) {
        videoEncoder.setFps(fps);
    }

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Get preview state.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isOnPreview() {
        return onPreview;
    }

    /**
     * Get record state.
     *
     * @return true if recording, false if not recoding.
     */
    public boolean isRecording() {
        return recordController.isRunning();
    }

    public void pauseRecord() {
        recordController.pauseRecord();
    }

    public void resumeRecord() {
        recordController.resumeRecord();
    }

    public RecordController.Status getRecordStatus() {
        return recordController.getStatus();
    }




    protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

    @Override
    public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        recordController.recordAudio(aacBuffer, info);
        if (streaming) getAacDataRtp(aacBuffer, info);
    }

    protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        onSpsPpsVpsRtp(sps.duplicate(), pps.duplicate(), vps != null ? vps.duplicate() : null);
    }

    protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        fpsListener.calculateFps();
        recordController.recordVideo(h264Buffer, info);
        if (streaming) getH264DataRtp(h264Buffer, info);
    }

    @Override
    public void inputPCMData(Frame frame) {
        audioEncoder.inputPCMData(frame);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
        recordController.setVideoFormat(mediaFormat, !audioInitialized);
    }

    @Override
    public void onAudioFormat(MediaFormat mediaFormat) {
        recordController.setAudioFormat(mediaFormat);
    }

    public void setRecordController(BaseRecordController recordController) {
        if (!isRecording()) this.recordController = recordController;
    }

    public abstract void setLogs(boolean enable);

    public abstract void setCheckServerAlive(boolean enable);

    public abstract void setAudioInit(boolean enable);
}