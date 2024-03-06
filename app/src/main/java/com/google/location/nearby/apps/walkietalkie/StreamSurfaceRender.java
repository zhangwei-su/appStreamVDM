package com.google.location.nearby.apps.walkietalkie;

import static com.google.location.nearby.apps.walkietalkie.VDMController.STREAMING_APP_HEIGTH;
import static com.google.location.nearby.apps.walkietalkie.VDMController.STREAMING_APP_WIDTH;

import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;

public class StreamSurfaceRender {
    private final static String TAG = "Wei-StreamSurfaceRender";
    private MediaCodec decoder;
    private Surface surface;

    public StreamSurfaceRender(Surface surface) {
        this.surface = surface;
        try {
            // Initialize the MediaCodec decoder with the selected format
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, STREAMING_APP_WIDTH, STREAMING_APP_HEIGTH);
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            decoder.configure(format, surface, null, 0);
            decoder.start();
            Log.d(TAG, "startDecoder: decoder.start done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final long TIMEOUT_US = 10000;
    public void startDecoder(InputStream inputStream) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "startDecoder: STREAM payload,size=" + inputStream.available());
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int frameRate = 20; // Frame rate in frames per second (fps)
                    long startTime = System.nanoTime() / 1000; // Start time in microseconds
                    long presentationTimeUs = startTime; // Initial presentation time
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        ByteBuffer[] inputBuffers = decoder.getInputBuffers();
                        int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(buffer, 0, bytesRead);
                            // Calculate presentation time for the current frame
                            presentationTimeUs += (1_000_000 / frameRate); // Presentation time per frame (microseconds)

                            decoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0);
                            Log.d(TAG, "startDecoder: dequeueInputBuffer bytesRead=" + bytesRead
                                    + ",presentationTimeUs=" + presentationTimeUs);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void stopRender() {
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
    }
}
