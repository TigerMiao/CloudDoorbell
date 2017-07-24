package com.projects.tiger.clouddoorbell;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import com.google.android.things.contrib.driver.button.Button;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DoorbellActivity extends Activity {
    private static final String TAG = DoorbellActivity.class.getSimpleName();
    private static final String BUTTON_PIN_NAME = "BCM21";

    private Button mButton;

    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    private DoorbellCamera mCamera;

    private ImageView photo;
    private Bitmap bitmap;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == 0x01) {
                photo.setImageBitmap(bitmap);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        photo = findViewById(R.id.photo);

        Log.d(TAG, "Doorbell Activity created.");

        try {
            mButton = new Button(BUTTON_PIN_NAME, Button.LogicState.PRESSED_WHEN_LOW);
            mButton.setOnButtonEventListener(mButtonCallback);
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }

        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);
    }

    private Button.OnButtonEventListener mButtonCallback = new Button.OnButtonEventListener() {
        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                // Doorbell rang!
                Log.d(TAG, "button pressed");

                mCamera.takePicture();
            }
        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Image image = imageReader.acquireLatestImage();
            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();

            onPictureTaken(imageBytes);
        }
    };

    private void onPictureTaken(final byte[] imageBytes) {
        if (imageBytes != null) {
            bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            handler.sendEmptyMessage(0x01);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            mButton.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }

        mCamera.shutDown();

        mCameraThread.quitSafely();
    }
}
