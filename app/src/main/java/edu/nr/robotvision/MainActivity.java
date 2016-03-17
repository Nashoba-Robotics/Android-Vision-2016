package edu.nr.robotvision;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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
import org.opencv.core.CvType;
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
    int H_high = 90;
    int S_high = 255;
    int S_low = 78;
    int L_low = 50;
    int L_high = 200;
    Scalar low = new Scalar(H_low, L_low, S_low);
    Scalar high = new Scalar(H_high, L_high, S_high);

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
        final int WIDTH = 1280; //Width of the screen

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
            if (largest == -1)
                return rgba;

            MatOfPoint goodPoly = prunedPoly.get(largest);
            if(goodPoly.toArray().length < 4) {
                System.out.println("Good poly issue");
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


            final double xCenterOfTarget = width/2.0 + tlcornerX;
            final double yCenterOfTarget = height/2.0 + tlcornerY;
            final double leftRightPixels = xCenterOfTarget - WIDTH/2.0;
            final int FOVH = 60;
            double turn = (FOVH/(1.0 * WIDTH)) * leftRightPixels ;
            final double distance = (height-128)/(-3.7);

            turn = turn + Math.asin(11 / 16 / distance);
            System.out.println("Height: " + height + ", size: " + size + ", turn: " + turn);
            //Imgproc.putText(rgba,String.valueOf(distance),new Point(150,150),0,1,new Scalar(255,255,0),1,8,true);
            // Output the final image
            Mat output = inputFrame.rgba();
            Core.flip(output, output, 0);
            Core.flip(output, output, 1);

            for (int i = 0; i < prunedPoly.size(); i++) {
                Imgproc.drawContours(output, prunedPoly, i, new Scalar(0, 0, 255),5,8,hierarchy,0, new Point(0,0));
            }
            return output;
        }
        return rgba;
    }
}