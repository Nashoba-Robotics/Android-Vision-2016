package edu.nr.robotvision;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

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

        ArrayList<MatOfInt> hulls = new ArrayList<>();

        for(int i = 0; i < contours.size(); i++ ) {
            MatOfInt mat = new MatOfInt();
            Imgproc.convexHull(contours.get(i), mat);
            hulls.add(mat);
        }



        // Approximate the convex hulls with polygons
        // This reduces the number of edges and makes the contours
        // into quads
        ArrayList<MatOfPoint2f> poly2f = new ArrayList<>();
        int poly_epsilon = 10;

        for (int i=0; i < contours.size(); i++) {

            MatOfPoint2f thisContour2f = new MatOfPoint2f();
            MatOfPoint2f approxContour2f = new MatOfPoint2f();

            contours.get(i).convertTo(thisContour2f, CvType.CV_32FC2);

            Imgproc.approxPolyDP(thisContour2f, approxContour2f, poly_epsilon, true);

            poly2f.add(approxContour2f);

            // These come out reversed, so reverse back
            /*List<Point> polyi = poly.get(i).toList();
            MatOfPoint2f mat = new MatOfPoint2f().fromList(polyi)
            poly.set(i, mat);*/
        }

        ArrayList<MatOfPoint> poly = new ArrayList<>();
        for(MatOfPoint2f mat : poly2f) {
            MatOfPoint contour = new MatOfPoint();
            mat.convertTo(contour, CvType.CV_32F);
            poly.add(contour);
        }

        int minArea = 1000; //Minimum area for the detected rectangle to have
        final int WIDTH = 1280; //Width of the screen

        ArrayList<MatOfPoint> prunedPoly = new ArrayList<>();
        if (poly.size() > 0) {
            int size = minArea;
            int largest = -1;
            for (int i = 0; i < poly.size(); i++) {
                MatOfPoint points = poly.get(i);
                System.out.println("Depth == " + points.depth());
                System.out.println("CvType.CV_32F == " + CvType.CV_32F);
                System.out.println("Vector(2) == " + points.checkVector(2));
                System.out.println((points.checkVector(2) >= 0 && (points.depth() == CvType.CV_32F || points.depth() == CvType.CV_32S)));
                //The next line causes it to crash with
                //OpenCV Error: Assertion failed (points.checkVector(2) >= 0 && (points.depth() == CV_32F || points.depth() == CV_32S)) in cv::Rect cv::boundingRect(InputArray), file /hdd2/buildbot/slaves/slave_ardbeg1/50-SDK/opencv/modules/imgproc/src/contours.cpp, line 1895
                //This is despite the fact that all those things it is asserting all return true
                //Rect bRect = Imgproc.boundingRect(points);
                /*/// Remove polygons that are too small
                if (bRect.width * bRect.height > minArea) {
                    prunedPoly.add(poly.get(i));
                    if (bRect.width * bRect.height > size) {
                        size = bRect.width * bRect.height;
                        largest = prunedPoly.size() - 1;
                    }
                }*/
            }
            /*//There are no targest bigger than the minArea
            if (largest == -1)
                return rgba;
            MatOfPoint goodPoly = prunedPoly.get(largest);
            // Output the final image
            for (int i = 0; i < prunedPoly.size(); i++) {
                Imgproc.drawContours(rgba, prunedPoly, i, new Scalar(0, 0, 255));
            }*/
        }
        return inputFrame.rgba();
    }
}