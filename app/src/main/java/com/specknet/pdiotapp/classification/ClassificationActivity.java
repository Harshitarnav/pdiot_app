package com.specknet.pdiotapp.classification;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.specknet.pdiotapp.R;
import com.specknet.pdiotapp.utils.Constants;
import com.specknet.pdiotapp.utils.GyroscopeReading;
import com.specknet.pdiotapp.utils.RESpeckLiveData;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;

public class ClassificationActivity extends AppCompatActivity {

    final String[] labels = { "Climbing stairs",
                              "Descending stairs",
                              "Desk work",
                              "Lying down left",
                              "Lying down on back",
                              "Lying down on stomach",
                              "Lying down right",
                              "Movement",
                              "Running",
                              "Sitting",
                              "Sitting bent backward",
                              "Sitting bent forward",
                              "Standing",
                              "Walking at normal speed" };

    public BroadcastReceiver respeckLiveUpdateReceiver;
    public Looper looperRespeck;
    private final IntentFilter filterTestRespeck = new IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST);

    private EditText input;
    private TextView output;
    Interpreter tflite;

    Queue<float[]> window = new LinkedList<>();

    void initWindow(int size) {
        for (int i = 0; i < size; i++) {
            window.add(new float[6]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classification);

        input = (EditText) findViewById(R.id.input);
        output = (TextView) findViewById(R.id.output);
        Button button = (Button) findViewById(R.id.compute_button);

        initWindow(50);

        try {
            tflite = new Interpreter(loadModelFile());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        getDataPoint();

//        button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                float prediction = inference(input.getText().toString());
//                output.setText(Float.toString(prediction));
//            }
//        });


    }

    public float inference(Queue window) {
        float[][][] inputValue = new float[1][50][6];
        int idx = 0;
        for (Object o : window) {
            inputValue[0][idx] = (float[]) o;
            idx++;
        }

        float[][] outputValue = new float[1][14];
        tflite.run(inputValue, outputValue);

        float maxValue = outputValue[0][0];
        int maxIndex = -1;

        for (int i = 0; i < 14; i++) {
            if (maxValue < outputValue[0][i]) {
                maxValue = outputValue[0][i];
                maxIndex = i;
            }
        }

        if (maxIndex != -1) {
            String predictedLabel = labels[maxIndex];
            output.setText(predictedLabel);
        }


        return maxValue;
    }

    public void displayData(float[] data) {
        inference(window);
    }

    public float[] getDataPoint() {

        // Order for CNN
        // [accelX, accelY, accelZ, gyroX, gyroY, gyroZ]

        float[] data = new float[6];

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().getName());

                String action = intent.getAction();

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    RESpeckLiveData liveData = (RESpeckLiveData) intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA);
                    Log.d("Live", "onReceive: liveData = " + liveData);

                    // get all relevant intent contents
                    data[0] = liveData.getAccelX();
                    data[1] = liveData.getAccelY();
                    data[2] = liveData.getAccelZ();

                    GyroscopeReading gyro = liveData.getGyro();
                    data[3] = gyro.getX();
                    data[4] = gyro.getY();
                    data[5] = gyro.getZ();

                    window.remove();
                    window.add(data);

                    // Call to function processing the data must be here
                    displayData(data);

                }
            }
        };

        // register receiver on another thread
        HandlerThread handlerThreadRespeck = new HandlerThread("bgThreadRespeckLive");
        handlerThreadRespeck.start();
        looperRespeck = handlerThreadRespeck.getLooper();
        Handler handlerRespeck = new Handler(looperRespeck);
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck);

        return data;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("respeck_cnn.tflite");
        FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffSets = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSets, declaredLength);
    }
}