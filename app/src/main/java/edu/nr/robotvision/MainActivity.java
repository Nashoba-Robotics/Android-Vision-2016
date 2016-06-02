package edu.nr.robotvision;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;


public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, SensorEventListener
{
    private CameraBridgeViewBase camView;
    private final int PERMISSION_REQUEST_ALLOW_CAMERA = 1;


    //Angle stuff:
    double gravity[] = {0,0,0};

    boolean initialized = false;

    double camera_angle = 0; // in radians

    static double turn = 0;
    static double distance = 0;
    static long delta_time = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler(this));

        Log.d("MainActivity", "On create");

        Toast.makeText(this, "ON CREATE", Toast.LENGTH_SHORT).show();

        //Makes the app fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Inflates the views from the xml file
        setContentView(R.layout.activity_main);


        //Setup roboRIO comm
        new Thread(new Server()).start();


        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sensorManager.registerListener(this, gravitySensor, 10000);


        camView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        camView.setVisibility(SurfaceView.VISIBLE);
        camView.setCvCameraViewListener(this);

        if (this.checkSelfPermission(
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
        {

                this.requestPermissions(
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSION_REQUEST_ALLOW_CAMERA);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
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
        if(camView != null)
            camView.enableView();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if(camView != null)
            camView.disableView();
        Toast.makeText(this, "ON PAUSE", Toast.LENGTH_SHORT).show();

        //Maybe send a message to the roboRIO here
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
    int H_low = 30;//55
    int H_high = 75;
    int S_high = 255;
    int S_low = 78;
    int L_low = 50;
    int L_high = 200;
    final Scalar low = new Scalar(H_low, L_low, S_low);
    final Scalar high = new Scalar(H_high, L_high, S_high);

    final int WIDTH = 600;
    final int HEIGHT = 800;

    final double FOVH = 66; //in degrees
    final double FOVW = 49.5 * (Math.PI/180); //horizontal field of view in radians


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {

        long startTime = System.currentTimeMillis();

        Mat rgba = inputFrame.rgba();
        int dilationSize = 2;
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * dilationSize + 1, 2 * dilationSize + 1), new Point(dilationSize, dilationSize));


        //Core.transpose(rgba, rgba);
        Core.flip(rgba, rgba, 0);
        Core.flip(rgba, rgba, 1);


        Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_BGR2HLS);

        Core.inRange(rgba, low, high, rgba);

        Imgproc.dilate(rgba, rgba, dilateElement);

        Mat canny_output = new Mat();

        int thresh = 200;

        Imgproc.Canny(rgba, canny_output, thresh, thresh * 2);

        Mat hierarchy = new Mat();

        ArrayList<MatOfPoint> contours = new ArrayList<>();

        Imgproc.findContours(canny_output, contours, hierarchy, Imgproc.CV_RETR_TREE, Imgproc.CV_CHAIN_APPROX_SIMPLE, new Point(0, 0));

        // Find the convex hull
        ArrayList<MatOfInt> hull = new ArrayList<>();
        for(int i=0; i < contours.size(); i++){
            hull.add(new MatOfInt());
        }
        for(int i=0; i < contours.size(); i++){
            Imgproc.convexHull(contours.get(i), hull.get(i));
        }

        // Convert MatOfInt to MatOfPoint for drawing convex hull

        // Loop over all contours
        ArrayList<Point[]> hullpoints = new ArrayList<>();
        for(int i=0; i < hull.size(); i++){
            Point[] points = new Point[hull.get(i).rows()];

            // Loop over all points that need to be hulled in current contour
            for(int j=0; j < hull.get(i).rows(); j++){
                int index = (int)hull.get(i).get(j, 0)[0];
                points[j] = new Point(contours.get(i).get(index, 0)[0], contours.get(i).get(index, 0)[1]);
            }

            hullpoints.add(points);
        }

        // Convert Point arrays into MatOfPoint2f
        ArrayList<MatOfPoint2f> hullmop = new ArrayList<>();
        for(int i=0; i < hullpoints.size(); i++){
            MatOfPoint2f mop = new MatOfPoint2f();
            mop.fromArray(hullpoints.get(i));
            hullmop.add(mop);
        }

        // Approximate the convex hulls with polygons
        // This reduces the number of edges and makes the contours
        // into quads
        ArrayList<MatOfPoint2f> poly2f = new ArrayList<>();
        for(int i=0; i < hullmop.size(); i++){
            poly2f.add(new MatOfPoint2f());
        }
        final int poly_epsilon = 10;
        for (int i=0; i < hullmop.size(); i++) {
            Imgproc.approxPolyDP(hullmop.get(i), poly2f.get(i), poly_epsilon, true);
        }

        ArrayList<MatOfPoint> poly = new ArrayList<>();
        // Convert MatOfPoint2f into MatOfPoint
        for(int i=0; i < poly2f.size(); i++) {
            MatOfPoint mop = new MatOfPoint();
            mop.fromArray(poly2f.get(i).toArray());
            poly.add(mop);
        }

        final int minArea = 1000; //Minimum area for the detected rectangle to have

        ArrayList<MatOfPoint> prunedPoly = new ArrayList<>();
        if (poly.size() > 0) {

            int size = minArea;
            int largest = -1;
            for (int i = 0; i < poly.size(); i++) {
                Rect bRect = Imgproc.boundingRect(poly.get(i));
                /// Remove polygons that are too small

                if (Math.abs(bRect.width * bRect.height) > minArea) {
                    prunedPoly.add(poly.get(i));
                    if (bRect.width * bRect.height > size) {
                        size = bRect.width * bRect.height;
                        largest = prunedPoly.size() - 1;
                    }
                }
            }
            //There are no targets bigger than the minArea
            if (largest == -1) {

                Core.flip(rgba, rgba, 1);

                Imgproc.putText(rgba, "UD tilt: " + String.valueOf(camera_angle * 180 / Math.PI), new Point(50, 300), 0, 2, new Scalar(255, 0, 0), 5, 8, true);

                Core.flip(rgba, rgba, 0);


                return rgba;
            }

            MatOfPoint goodPoly = prunedPoly.get(largest);
            if(goodPoly.toArray().length < 4) {
                System.out.println("Good poly issue");
                Core.flip(rgba, rgba, 1);

                Imgproc.putText(rgba, "UD tilt: " + String.valueOf(camera_angle * 180 / Math.PI), new Point(50, 300), 0, 2, new Scalar(255, 0, 0), 5, 8, true);


                Core.flip(rgba, rgba, 0);


                return rgba;
            }
            //Determine the topLeft corner x
            final double tlcornerX = Math.min(Math.min(goodPoly.toArray()[0].x, goodPoly.toArray()[1].x), Math.min(goodPoly.toArray()[2].x, goodPoly.toArray()[3].x));
            final double tlcornerY = Math.min(Math.min(goodPoly.toArray()[0].y, goodPoly.toArray()[1].y), Math.min(goodPoly.toArray()[2].y, goodPoly.toArray()[3].y));

            //Determine the width and height
            final double x1 = (Math.abs(goodPoly.toArray()[0].x - goodPoly.toArray()[1].x) + Math.abs(goodPoly.toArray()[2].x - goodPoly.toArray()[3].x))/2;
            final double x2 = (Math.abs(goodPoly.toArray()[1].x - goodPoly.toArray()[2].x) + Math.abs(goodPoly.toArray()[3].x - goodPoly.toArray()[0].x))/2;
            final double width = Math.max(x1, x2);

            final double y1 = (Math.abs(goodPoly.toArray()[0].y - goodPoly.toArray()[1].y) + Math.abs(goodPoly.toArray()[2].y - goodPoly.toArray()[3].y))/2;
            final double y2 = (Math.abs(goodPoly.toArray()[1].y - goodPoly.toArray()[2].y) + Math.abs(goodPoly.toArray()[3].y - goodPoly.toArray()[0].y))/2;
            final double height = Math.max(y1, y2);

            final double yCenterOfTarget = width/2.0 + tlcornerX;
            final double xCenterOfTarget = height/2.0 + tlcornerY;
            final double heightFromBottom = HEIGHT-yCenterOfTarget;
            final double leftRightPixels = xCenterOfTarget - WIDTH/2.0;
            final double x = heightFromBottom;
            turn = ((FOVW/WIDTH) * leftRightPixels) * 180 / Math.PI; //in degrees
            //turn = 90 - Math.atan2(distance, 0.55); //0.55 is the left offset in feet

            turn -= -0.0106232*x + 1.4051369;
            distance = -0.000000221605 * x * x * x + 0.000401265632 * x * x - 0.257085878516 * x + 63.199508056832;


            // Output the final image
            Mat output = inputFrame.rgba();
            Core.flip(output, output, 1);

            Imgproc.putText(output, String.valueOf(distance), new Point(50, 500), 0, 3, new Scalar(255, 0, 0), 5, 8, true);
            Imgproc.putText(output, "LR turn: " + String.valueOf(turn), new Point(50, 400), 0, 2, new Scalar(255, 0, 0), 5, 8, true);
            Imgproc.putText(output, "UD tilt: " + String.valueOf(camera_angle * 180 / Math.PI), new Point(50, 300), 0, 2, new Scalar(255, 0, 0), 5, 8, true);
            Core.flip(output, output, 0);

            for (int i = 0; i < prunedPoly.size(); i++) {
                Imgproc.drawContours(output, prunedPoly, i, new Scalar(0, 0, 255),5,8,hierarchy,0, new Point(0,0));
            }

            delta_time = System.currentTimeMillis() - startTime;

            return output;
        }
        Core.flip(rgba, rgba, 1);

        Imgproc.putText(rgba, "UD tilt: " + String.valueOf(camera_angle * 180 / Math.PI), new Point(50, 300), 0, 2, new Scalar(255, 0, 0), 5, 8, true);

        Core.flip(rgba, rgba, 0);

        return rgba;
    }

    public void onSensorChanged(SensorEvent event){
        // In this, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.

        double alpha = 0.8;

        if(!initialized) {
            alpha = 0;
            initialized = true;
        }

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        camera_angle = -Math.atan2(gravity[2], gravity[1]);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.e("MainActivity", "YOOOO!!!! ACCURACY CHANGED. Accuracy: " + accuracy );
    }
}
