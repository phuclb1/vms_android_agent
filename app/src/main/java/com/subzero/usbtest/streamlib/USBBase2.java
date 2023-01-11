package com.subzero.usbtest.streamlib;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.util.RecordController;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class USBBase2
        implements GetAacData, GetCameraData, GetVideoData, GetMicrophoneData {

    private static final String TAG = "USBBase2";

    private Context context;
    protected VideoEncoder videoEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;
//    private SurfaceView surfaceView;
//    private TextureView textureView;
    private GlInterface glInterface;
    private boolean streaming = false;
    private boolean videoEnabled = true;
    private boolean isBackground = false;
    protected RecordController recordController;
    private int previewWidth, previewHeight;
    private FpsListener fpsListener = new FpsListener();
    //record
//    private MediaMuxer mediaMuxer;
//    private int videoTrack = -1;
//    private int audioTrack = -1;
    private boolean recording = false;
//    private boolean canRecord = false;
    private boolean onPreview = false;
//    private MediaFormat videoFormat;
//    private MediaFormat audioFormat;

    public USBBase2(OpenGlView openGlView) {
        context = openGlView.getContext();
        this.glInterface = openGlView;
        this.glInterface.init();
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public USBBase2(LightOpenGlView lightOpenGlView) {
        context = lightOpenGlView.getContext();
        this.glInterface = lightOpenGlView;
        this.glInterface.init();
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
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
        audioEncoder = new AudioEncoder(this);
        recordController = new RecordController();
    }

    public abstract void setAuthorization(String user, String password);

    public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
                                int iFrameInterval, int rotation, UVCCamera uvcCamera) {
        if (onPreview&& !(glInterface != null && width == previewWidth && height == previewHeight)) {
            stopPreview(uvcCamera);
            onPreview = true;
        }
        videoEnabled = true;
        return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
                iFrameInterval, FormatVideoEncoder.SURFACE);
    }

    /**
     * backward compatibility reason
     */
    public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
                                int rotation, UVCCamera uvcCamera) {
        return prepareVideo(width, height, fps, bitrate, hardwareRotation, 2, rotation, uvcCamera);
    }

    protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

    /**
     * Call this method before use @startStream. If not you will do a stream without audio.
     *
     * @param bitrate         AAC in kb.
     * @param sampleRate      of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo        true if you want Stereo audio (2 audio channels), false if you want Mono audio
     *                        (1 audio channel).
     * @param echoCanceler    true enable echo canceler, false disable.
     * @param noiseSuppressor true enable noise suppressor, false  disable.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
        prepareAudioRtp(isStereo, sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo, microphoneManager.getMaxInputSize());
    }

    /**
     * Same to call: rotation = 0; if (Portrait) rotation = 90; prepareVideo(640, 480, 30, 1200 *
     * 1024, false, rotation);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    public boolean prepareVideo(UVCCamera uvcCamera) {
        int rotation = CameraHelper.getCameraOrientation(context);
        return prepareVideo(640, 480, 30, 1200 * 1024, false, rotation, uvcCamera);
    }

    /**
     * Same to call: prepareAudio(64 * 1024, 32000, true, false, false);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio() {
        return prepareAudio(64 * 1024, 32000, true, false, false);
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
        videoEncoder.setForce(forceVideo);
        audioEncoder.setForce(forceAudio);
    }

    public void startRecord(String path, RecordController.Listener listener, UVCCamera uvcCamera) throws IOException {
        recordController.startRecord(path, listener);
        if (!streaming) {
            startEncoders(uvcCamera);
        } else if (videoEncoder.isRunning()) {
            resetVideoEncoder(uvcCamera);
        }
    }

    public void startRecord(final String path, UVCCamera uvcCamera) throws IOException {
        startRecord(path, null, uvcCamera);
    }

    public void stopRecord(UVCCamera uvcCamera) {
        recordController.stopRecord();
        if (!streaming) stopStream(uvcCamera);
    }

    public void replaceView(UVCCamera uvcCamera, Context context) {
        isBackground = true;
        replaceGlInterface(uvcCamera, new OffScreenGlThread(context));
    }

    public void replaceView(UVCCamera uvcCamera, OpenGlView openGlView) {
        isBackground = false;
        replaceGlInterface(uvcCamera, openGlView);
    }

    public void replaceView(UVCCamera uvcCamera, LightOpenGlView lightOpenGlView) {
        isBackground = false;
        replaceGlInterface(uvcCamera, lightOpenGlView);
    }

    private void replaceGlInterface(final UVCCamera uvcCamera, GlInterface glInterface) {
        if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
            if (isStreaming() || isRecording() || isOnPreview()) {
                uvcCamera.stopPreview();
                this.glInterface.removeMediaCodecSurface();
                this.glInterface.stop();
                this.glInterface = glInterface;
                this.glInterface.init();
                boolean isPortrait = CameraHelper.isPortrait(context);
                if (isPortrait) {
                    this.glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
                } else {
                    this.glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
                }
                this.glInterface.setRotation(
                        videoEncoder.getRotation() == 0 ? 270 : videoEncoder.getRotation() - 90);
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
     * Start camera preview. Ignored, if stream or preview is started.
     *
     * @param width  of preview in px.
     * @param height of preview in px.
     */
    public void startPreview(final UVCCamera uvcCamera, int width, int height, int rotation) {
        if (!isStreaming() && !onPreview && !isBackground) {
            previewWidth = width;
            previewHeight = height;

            if (glInterface != null) {
                boolean isPortrait = CameraHelper.isPortrait(context);
                if (!isPortrait) {
                    glInterface.setEncoderSize(height, width);
                } else {
                    glInterface.setEncoderSize(width, height);
                }
//                glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
                glInterface.setRotation(rotation);
                glInterface.start();
                uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
                uvcCamera.startPreview();
            }
            onPreview = true;
        }
    }

    /**
     * Stop camera preview. Ignored if streaming or already stopped. You need call it after
     *
     * @stopStream to release camera properly if you will close activity.
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

    protected abstract void startStreamRtp(String url);

    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *            <p>
     *            RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     *            RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */
    public void startStream(UVCCamera uvcCamera, String url) {
        streaming = true;
        if (!recordController.isRunning()) {
            startEncoders(uvcCamera);
        } else {
            resetVideoEncoder(uvcCamera);
        }
        startStreamRtp(url);
        onPreview = true;
    }

    private void startEncoders(UVCCamera uvcCamera) {
        videoEncoder.start();
        audioEncoder.start();
        prepareGlView(uvcCamera);
        microphoneManager.start();
        if (glInterface == null && videoEncoder.getWidth() != previewWidth
                || videoEncoder.getHeight() != previewHeight) {
            uvcCamera.setPreviewTexture(glInterface.getSurfaceTexture());
            uvcCamera.startPreview();
        }
        onPreview = true;
    }

    private void resetVideoEncoder(UVCCamera uvcCamera) {
        if (glInterface != null) {
            glInterface.removeMediaCodecSurface();
        }
        videoEncoder.reset();
        if (glInterface != null) {
            glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        } else {
            uvcCamera.stopPreview();
//            cameraManager.closeCamera();
//            cameraManager.prepareCamera(videoEncoder.getInputSurface());
//            cameraManager.openLastCamera();
        }
    }

    private void prepareGlView(UVCCamera uvcCamera) {
        if (glInterface != null && videoEnabled) {
            if (glInterface instanceof OffScreenGlThread) {
                glInterface = new OffScreenGlThread(context);
                glInterface.init();
            }
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

    protected abstract void stopStreamRtp();

    /**
     * Stop stream started with @startStream.
     */
    public void stopStream(UVCCamera uvcCamera) {
        if (streaming) {
            streaming = false;
            stopStreamRtp();
        }
        if (!recordController.isRecording()) {
            onPreview = !isBackground;
            microphoneManager.stop();
            if (glInterface != null) {
                glInterface.removeMediaCodecSurface();
                if (glInterface instanceof OffScreenGlThread) {
                    glInterface.stop();
                    uvcCamera.stopPreview();
//                    uvcCamera.close();
                }
            } else {
                if (isBackground) {
//                    uvcCamera.close();
                } else {
                }
            }
            videoEncoder.stop();
            audioEncoder.stop();
            recordController.resetFormats();
        }
    }

    public boolean reTry(long delay, String reason, UVCCamera uvcCamera) {
        boolean result = shouldRetry(reason);
        if (result) {
            reTry(delay, uvcCamera);
        }
        return result;
    }

    public void reTry(long delay, UVCCamera uvcCamera) {
        resetVideoEncoder(uvcCamera);
        reConnect(delay);
    }

    public abstract boolean shouldRetry(String reason);

    public abstract void setReTries(int reTries);

    protected abstract void reConnect(long delay);

    //cache control
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
     * Mute microphone, can be called before, while and after stream.
     */
    public void disableAudio() {
        microphoneManager.mute();
    }

    /**
     * Enable a muted microphone, can be called before, while and after stream.
     */
    public void enableAudio() {
        microphoneManager.unMute();
    }

    /**
     * Get mute state of microphone.
     *
     * @return true if muted, false if enabled
     */
    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }

    /**
     * Get video camera state
     *
     * @return true if disabled, false if enabled
     */
    public boolean isVideoEnabled() {
        return videoEnabled;
    }

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
     *
     * @param bitrate H264 in kb.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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
        return recording;
    }

    protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

    @Override
    public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
        recordController.recordAudio(aacBuffer, info);
        if (streaming) getAacDataRtp(aacBuffer, info);
    }

    protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

    @Override
    public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, null);
    }

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, vps);
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
    public void inputYUVData(Frame frame) {
        videoEncoder.inputYUVData(frame);
    }
    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
        recordController.setVideoFormat(mediaFormat);
    }

    @Override
    public void onAudioFormat(MediaFormat mediaFormat) {
        recordController.setAudioFormat(mediaFormat);
    }

    public abstract void setLogs(boolean enable);
}