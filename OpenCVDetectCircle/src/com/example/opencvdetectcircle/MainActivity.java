package com.example.opencvdetectcircle;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;

public class MainActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Mat                  mHsva;
    private Mat                  mLower_red_hue_range;
    private Mat                  mUpper_red_hue_range;
    private Mat                  red_hue_image;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    //private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    List<MatOfPoint> contours;
    Mat circles;
    double mCannyUpperThreshold = 100;
    double mAccumulator = 30;
    int mMinRadius = 5;
    int mMaxRadius = 200;
    
    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        contours = new ArrayList<MatOfPoint>(); 
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
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mHsva = new Mat(height, width, CvType.CV_8UC4);
        mLower_red_hue_range = new Mat(height, width, CvType.CV_8UC4);
        mUpper_red_hue_range = new Mat(height, width, CvType.CV_8UC4);
        red_hue_image = new Mat(height, width, CvType.CV_8UC4);
        circles = new Mat();
        //mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mHsva.release();
        mLower_red_hue_range.release();
        mUpper_red_hue_range.release();
        red_hue_image.release();
    }
    
    public boolean onTouch(View v, MotionEvent event) {
    	/*
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();
    	*/
        return false; // don't need subsequent touch events
    }
    

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        Imgproc.medianBlur(mRgba, mRgba, 3);
        //Convert input frame to HSV
        Imgproc.cvtColor(mRgba, mHsva, Imgproc.COLOR_BGR2HSV_FULL);
        //Threshold the HSV image, keep only the red pixels
        Core.inRange(mHsva, new Scalar(0, 100, 100), new Scalar(10, 255, 255), mLower_red_hue_range);
        Core.inRange(mHsva, new Scalar(160, 100, 100), new Scalar(179, 255, 255), mUpper_red_hue_range);
        //Combine the above two images
        Core.addWeighted(mLower_red_hue_range, 1.0, mUpper_red_hue_range, 1.0, 0.0, red_hue_image);
        Imgproc.GaussianBlur(red_hue_image, red_hue_image, new Size(3,3), 2, 2);
        //Use the Hough transform to detect circles in the combined threshold image
		Imgproc.HoughCircles(red_hue_image, circles, Imgproc.CV_HOUGH_GRADIENT, 1.0, red_hue_image.rows() / 5, 
				mCannyUpperThreshold, mAccumulator, mMinRadius, mMaxRadius);
		Log.w("circles", circles.cols()+", "+circles.rows());
        
        //Loop over all detected circles and outline them on the original image
        for (int x = 0; x < circles.cols(); x++) 
        {
                double vCircle[] = circles.get(0,x);

                Point center = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
                int radius = (int)Math.round(vCircle[2]);
                // draw the circle center
                Imgproc.circle(mRgba, center, 3, new Scalar(0,255,0), -1, 8, 0 );
                // draw the circle outline
                Imgproc.circle(mRgba, center, radius, new Scalar(0,0,255), 3, 8, 0 );

        }
        
        //std::vector<cv::Vec3f> circles;
        //cv::HoughCircles(red_hue_image, circles, CV_HOUGH_GRADIENT, 1, red_hue_image.rows/8, 100, 20, 0, 0);
        /*
        if (mIsColorSelected) {
            mDetector.process(mHsva);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mHsva, contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mHsva.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mHsva.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }
		*/
        return mRgba;
    }
    
    /*
    private void findEllipses(Mat input){
    	Mat thresholdOutput = new Mat();
    	int thresh = 150;

    	List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    	MatOfInt4 hierarchy = new MatOfInt4();

    	Imgproc.threshold(source, thresholdOutput, thresh, 255, Imgproc.THRESH_BINARY);
    	//Imgproc.Canny(source, thresholdOutput, 50, 180);
    	Imgproc.findContours(source, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
    	RotatedRect minEllipse[] = new RotatedRect[contours.size()];

    	for(int i=0; i<contours.size();i++){
    	    MatOfPoint2f temp=new MatOfPoint2f(contours.get(i).toArray());

    	    if(temp.size().height > minEllipseSize && temp.size().height < maxEllipseSize){
    	        double a = Imgproc.fitEllipse(temp).size.height;
    	        double b = Imgproc.fitEllipse(temp).size.width;
    	        if(Math.abs(a - b) < 10)
    	            minEllipse[i] = Imgproc.fitEllipse(temp);
    	    }
    	}
    	detectedObjects.clear();
    	for( int i = 0; i< contours.size(); i++ ){
    	    Scalar color = new Scalar(180, 255, 180);
    	    if(minEllipse[i] != null){
    	        detectedObjects.add(new DetectedObject(minEllipse[i].center));
    	        DetectedObject detectedObj = new DetectedObject(minEllipse[i].center);
    	        Core.ellipse(source, minEllipse[i], color, 2, 8);
    	    }
    	}
    }
    */
    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
