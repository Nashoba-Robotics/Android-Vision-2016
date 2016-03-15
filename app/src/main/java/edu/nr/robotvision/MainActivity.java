package edu.nr.robotvision;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2
{
    private CameraBridgeViewBase camView;
    private final int PERMISSION_REQUEST_ALLOW_CAMERA = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        //Makes the app fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Inflates the views from the xml file
        setContentView(R.layout.activity_main);


        camView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        camView.setVisibility(SurfaceView.VISIBLE);
        camView.setCvCameraViewListener(this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED)
        {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_ALLOW_CAMERA);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(MainActivity.class.getName(), "OpenCV loaded successfully");
                    camView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSION_REQUEST_ALLOW_CAMERA:
            {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
                }
                else
                {
                    //TODO do something if user denied permission
                }
                return;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if(camView != null)
            camView.disableView();
        Toast.makeText(this, "ON PAUSE", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if(camView != null)
            camView.disableView();
        Toast.makeText(this, "ON DESTROY", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCameraViewStarted(int width, int height)
    {

    }

    @Override
    public void onCameraViewStopped()
    {

    }

    //HLS Thresholding
    int H_low = 60;
    int H_high = 106;//90;
    int S_high = 255;
    int S_low = 78;
    int L_low = 50;
    int L_high = 200;
    Scalar low = new Scalar(H_low, L_low, S_low);
    Scalar high = new Scalar(H_high, L_high, S_high);

    int count = 0;

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {
        Mat rgba = inputFrame.rgba();
        Mat rgbaTemp = rgba.clone();

//        Mat rotationMatrix = Imgproc.getRotationMatrix2D(new Point(rgba.cols()/2.0, rgba.rows()/2.0), 180, 1.0);
//
//        Imgproc.warpAffine(rgba, rgbaTemp, rotationMatrix, rgba.size());
//        rgba.release();
//        rotationMatrix.release();

        Imgproc.cvtColor(rgba, rgbaTemp, Imgproc.COLOR_BGR2HLS);
        rgba = rgbaTemp.clone();

        Core.inRange(rgba, low, high, rgbaTemp);
        rgba = rgbaTemp.clone();

//        if(count > 50)
//        {
//            System.gc();
//            Log.d(MainActivity.class.getName(), "Forcing Garbage Collection");
//            count = 0;
//        }
//        count++;

        return rgba;
    }
}


