package com.google.location.nearby.apps.walkietalkie;

import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import motorola.core_services.companion.MotoCompanionHelper;
import motorola.core_services.companion.MotoVirtualDevice;
public class VDMController {

    private final static String TAG = "Wei-VDMController";
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_WINDOWING_MODE_FREEFORM = 1 << 20;
    private static final int VIRTUAL_DISPLAY_FLAG_RDP = 1 << 21;
    private final static boolean DRAW_BACKGROUND_COLOR_ALLOWED = false;

    private Context mContext;
    private DisplayManager mDisplayManager;

    static public int STREAMING_APP_WIDTH = 540;
    static public int STREAMING_APP_HEIGTH = 1200;
    static public int STREAMING_APP_DPI = 240;
    static public float STREAMING_APP_DPI_SCALING = 1.0f;
    private Surface mCastSurface;
    private VirtualDeviceManager mVDM;
    private MotoVirtualDevice mMotoVirtualDevice;
    private VirtualDisplay mMotoVirtualDisplay;
    private int mAssociationId;
    private Handler mHandler;
    SurfaceCapture mSurfaceCapture = new SurfaceCapture();
    public VDMController (Context context, int associationId) {
        mContext = context;
        mAssociationId = associationId;
        mHandler = new Handler(mContext.getMainLooper()); // used in test only
        mVDM = (VirtualDeviceManager) mContext.getSystemService(Context.VIRTUAL_DEVICE_SERVICE);
    }

    VirtualDisplay getVirtualDisplay() {
        return mMotoVirtualDisplay;
    }
    public void prepareDisplay() {
        mMotoVirtualDevice = createVirtualDevice(mAssociationId);
        Log.d(TAG, "createVirtualDevice mMotoVirtualDevice:" + mMotoVirtualDevice);
        if (mMotoVirtualDevice != null) {
            mMotoVirtualDisplay = createVirtualDisplay(mMotoVirtualDevice);
            Log.d(TAG, "createVirtualDisplay mMotoVirtualDisplay:" + mMotoVirtualDisplay);
            // show pointer icon for Virtual Display
            mMotoVirtualDevice.setShowPointerIcon(true);
        }
    }
    public void start(OutputStream outputStream) {
        mSurfaceCapture.startCapture(outputStream);
    }
    public void stop() {
        mSurfaceCapture.stopCapture();
    }
    private MotoVirtualDevice createVirtualDevice(int associationId) {
        if (associationId <= 0) {
            Log.d(TAG, "createVirtualDevice mAssociationId invalid:" + associationId);
            return null;
        }
        return MotoCompanionHelper.createVirtualDevice(mVDM, associationId);
    }
    private VirtualDisplay createVirtualDisplay(MotoVirtualDevice motoVD) {
        VirtualDisplay ret;
        ret = motoVD.createVirtualDisplay(
                STREAMING_APP_WIDTH, STREAMING_APP_HEIGTH, STREAMING_APP_DPI,
                mSurfaceCapture.getSurface(),
                getVirtualDisplayFlags(),
                mContext.getMainExecutor(),
                mVDCallback
        );
        return ret;
    }
    private int getVirtualDisplayFlags() {
        // for desktop right now
        return getVirtualDisplayFlagsForDesktop();
    }

    private int getVirtualDisplayFlagsForMirror() {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
    }

    private int getVirtualDisplayFlagsForDesktop() {
        //TODO: temp remove VIRTUAL_DISPLAY_FLAG_SECURE
        //https://partnerissuetracker.corp.google.com/issues/315289482,
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
                //DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
                VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP |
                VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH |
                VIRTUAL_DISPLAY_FLAG_TRUSTED |
                VIRTUAL_DISPLAY_FLAG_RDP;

        return flags;
    }
    private VirtualDisplay.Callback mVDCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
            Log.d(TAG, "VirtualDisplay.Callback onPaused");
        }

        @Override
        public void onResumed() {
            super.onResumed();
            Log.d(TAG, "VirtualDisplay.Callback onResumed");
        }

        @Override
        public void onStopped() {
            super.onStopped();
            Log.d(TAG, "VirtualDisplay.Callback onStopped");
        }
    };
    private static final long TIMEOUT_US = 10000;
    public class SurfaceCapture {
        private MediaCodec mediaCodec;
        private Surface surface;
        private MediaCodec.BufferInfo bufferInfo;
        private ByteBuffer[] outputBuffers;
        private volatile boolean isCatching = false;

        public SurfaceCapture() {
            // Create a MediaCodec encoder
            try {
                isCatching = false;
                //com.motorola.mobiledesktop.srtp.video.VideoStream#createMediacodec
                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, STREAMING_APP_WIDTH, STREAMING_APP_HEIGTH);
                // Set encoder configuration, such as bitrate, frame rate, etc.
                format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, (float) 60);
                format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000);
                //com.motorola.mobiledesktop.srtp.video.VideoQuality#DEFAULT_VIDEO_BITRATE
                format.setInteger(MediaFormat.KEY_BIT_RATE, 8);
                //com.motorola.mobiledesktop.srtp.video.VideoQuality#VIDEO_FRAMERATE
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                //Must-needed com.motorola.mobiledesktop.H264Encoder#initMediaCodec
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                surface = mediaCodec.createInputSurface(); // Get the input Surface
                mediaCodec.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Surface getSurface() {
            return surface;
        }

        public void startCapture(final OutputStream outputStream) {
            bufferInfo = new MediaCodec.BufferInfo();
            outputBuffers = mediaCodec.getOutputBuffers();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    isCatching = true;
                    int outputBufferIndex = -1;
                    try {
                        while (isCatching) {
                            // Get output buffer index
                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                            if (outputBufferIndex >= 0) {
                                // Get output buffer
                                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);//outputBuffers[outputBufferIndex];
                                Log.d(TAG, "run: outputBuffer.hasArray=" + outputBuffer.hasArray() + ",bufferInfo.size=" + bufferInfo.size);
                                // Write the buffer data to OutputStream
                                byte[] bufferData = new byte[bufferInfo.size];
                                outputBuffer.get(bufferData);
                                outputStream.write(bufferData, 0, bufferInfo.size);
                                // Release the output buffer
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = -1;
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // Output format changed, adjust if needed
                            }
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (outputBufferIndex >= 0) mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        mediaCodec.stop();
                        mediaCodec.release();
                    }
                }
            }).start();
        }

        public void stopCapture() {
            isCatching = false;
        }
    }
}
