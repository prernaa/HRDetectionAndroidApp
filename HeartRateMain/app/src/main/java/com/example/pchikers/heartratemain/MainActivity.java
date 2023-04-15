package com.example.pchikers.heartratemain;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.view.SurfaceView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;

import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.lang.Math.abs;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String TAG = "Hello, Heart Rate!";
    private static final int numSecsInBuffer = 5;
    private static final int ACCEL_BUFFER_SIZE = 25*numSecsInBuffer;
    private static final int numSecsGraphUpdate = 2;
    private static final int graphUpdateCounter = 25*numSecsGraphUpdate;
    private static final double ZERO_CROSSING_AMP_THRES = 10.0;
    private int accelValCounter = 0;
    private double[] accelBufferMag = new double[ACCEL_BUFFER_SIZE];
    private static int globalHR = 0;

//    private CameraBridgeViewBase mOpenCvCameraView;
    private JavaCameraView mOpenCvCameraView;
    private boolean mIsJavaCamera = true;

    // charting
//    private static final Random RANDOM = new Random();
    private LineGraphSeries<DataPoint> series;
    private int lastX = 0;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Getting camera view");
//        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.mycameraview);
        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.mycameraview);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        Log.i(TAG, "Connecting camera listener");
        mOpenCvCameraView.setCvCameraViewListener(this);
        //charting
        GraphView graph = (GraphView) findViewById(R.id.graph);
        series = new LineGraphSeries<DataPoint>();
        graph.addSeries(series);
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(130);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(100);
        viewport.setScrollable(true);
    }
    private void addPoint(double mag) {
        // here, we choose to display max 10 points on the viewport and we scroll to end
        series.appendData(new DataPoint(lastX, mag), true, 1000);
        lastX = lastX+5;
    }
    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    public void onCameraViewStarted(int width, int height) {
    }
    public void onCameraViewStopped() {
    }
    private double[] medianFilter(double[] buffer, int win){
        double [] filtered = new double[buffer.length-win+1];
        for (int i = 0; i<=buffer.length-win; i++){
            double[] vals = new double[win];
            for (int w = 0; w<win; w++){
                vals[w] = buffer[i+w];
//                Log.d("val in win = ", String.valueOf(vals[w]));
            }
            Arrays.sort(vals);
            double median;
            if (win%2==0){ // even win size
                int firstIdx = (win/2)-1; // Eg: win = 4 (0,1,2,3), firstIdx = 1
                int secondIdx = firstIdx + 1;
                median = vals[firstIdx]+ vals[secondIdx];
            }
            else{ // odd win size
                int Idx = (win/2); // Eg: win = 5 (0,1,2,3,4), Idx = 2
                median = vals[Idx];
            }
//            Log.d("median = ", String.valueOf(median));
            filtered[i] = median;
        }

        return filtered;
    }
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mOpenCvCameraView.turnOnTheFlash();
        Mat imgframe = inputFrame.rgba();
        int w = imgframe.width();
        int h =  imgframe.height();
        Rect roi = new Rect((w/2)-(w/4), ((h/2)-(h/4)), (w/2), (h/2));
        Mat smallImg = new Mat(imgframe, roi);
        List<Mat> lRgb = new ArrayList<Mat>(3);
//        Core.split(imgframe, lRgb);
        Core.split(smallImg, lRgb);
        Mat mR = lRgb.get(0);
        Scalar sc = Core.mean(mR);
        double x = sc.val[0];
//        Log.d("Out: ", String.valueOf(x));
        // Load new values in buffer
        accelBufferMag[accelValCounter % ACCEL_BUFFER_SIZE] = x;
        // update counter for samples
        accelValCounter++;
        // Perform calculations on buffer
        if (accelValCounter % ACCEL_BUFFER_SIZE == 0){
            // Local buffer
            double[] accelBufferMag_NORM = new double[ACCEL_BUFFER_SIZE];
            // De-meaning
            double sum = 0;
            for (int i = 0; i < accelBufferMag.length; i++) {
                sum += accelBufferMag[i];
            }
            double mean = sum / ((double) accelBufferMag.length);
            for (int i = 0; i < accelBufferMag.length; i++)
            {
                accelBufferMag_NORM[i] = accelBufferMag[i] - mean;
            }
            // Median filtering, changes size of local buffer s.t. newlen = len-win+1
            int win = 2;
            if (accelValCounter>ACCEL_BUFFER_SIZE) {
                accelBufferMag_NORM = medianFilter(accelBufferMag_NORM, win);
            }
            // Zero crossing
            int numCrossing = 0;
            for (int p = 0; p < accelBufferMag_NORM.length - 1; p++)
            {
                if ((accelBufferMag_NORM[p] > 0 && accelBufferMag_NORM[p + 1] <= 0) ||
                        (accelBufferMag_NORM[p] < 0 && accelBufferMag_NORM[p + 1] >= 0))
                {
                    numCrossing++;
                }
            }
            int hr = (numCrossing/2)*(60/numSecsInBuffer);
            Log.d("Out: ", String.valueOf(hr));
            if (x > 200) { // ensures that finger is in front of camera
                if ((hr>40) && (hr<130)) {
                    globalHR = hr;
                    final int finalHR = hr;
//                addPoint(hr);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView hrtxt = (TextView) findViewById(R.id.hrtxt);
                            hrtxt.setText(String.valueOf(finalHR));
                        }
                    });
                }
            }
            else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView hrtxt = (TextView) findViewById(R.id.hrtxt);
                        hrtxt.setText(String.valueOf("Initializing..."));
                    }
                });
            }


//            int numBeatsInBuffer = numCrossing/2;
//            Log.d("Out: ", String.valueOf(numBeatsInBuffer));
//            int numBeatsPerMinute = (60*numBeatsInBuffer)/numSecsInBuffer;
//            Log.d("Out: ", String.valueOf(numBeatsPerMinute));

        } // per 5 seconds loop ends here
//        Mat imgframeT = inputFrame.rgba();
//        Core.transpose(imgframe, imgframeT);
//        Imgproc.resize(imgframeT, imgframeT, imgframe.size());
        if (accelValCounter % graphUpdateCounter == 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addPoint(globalHR);
                }
            });
        }

        return imgframe;
    }

}
