//import java.io.File;
//import java.net.URL;
import java.nio.Buffer;

import org.bytedeco.javacpp.opencv_core.CvMat;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.*;
//import org.bytedeco.javacpp.*;
//import org.bytedeco.javacpp.indexer.*;
//import static org.bytedeco.javacpp.opencv_core.*;
//import static org.bytedeco.javacpp.opencv_imgproc.*;
//import static org.bytedeco.javacpp.opencv_calib3d.*;
//import static org.bytedeco.javacpp.opencv_objdetect.*;

//Taken from: https://github.com/bytedeco/javacv#sample-usage

public class InstantCloudCameraRunner implements Runnable {
	private final String PREFIX = "myvideo";
	private final static String BUCKETNAME = "icc-videostream-00";
	private final static int MAX_SEGMENTS = 10;
	private final double SEGMENT_VIDEOLENGTH = 8; //seconds
	private final double FPS = 30;
	private CanvasFrame canvas = new CanvasFrame("Instant Cloud Camera");
	private static S3Uploader s3;
	private static SharedQueue<String> que;
	
	//NEED BETTER CLOSING
	public InstantCloudCameraRunner() {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
	@Override
	public void run() {
		int segmentNum = 0;
		String outputFileName = PREFIX + segmentNum + ".flv";
		FrameGrabber grabber = null;
		ICCRecorder myRecorder = null;
//		https://stackoverflow.com/questions/28494648/video-compression-using-javacv
		try{
			grabber = FrameGrabber.createDefault(0); // 1 for next camera
			grabber.setFrameRate(FPS);
			grabber.start();
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			Frame img;
			Thread writeThread;
			int segmentLength = (int)(FPS * SEGMENT_VIDEOLENGTH);
			IplImage[] myImage = new IplImage[segmentLength];
			OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
//			System.out.println("Frame Rate: " + grabber.getFrameRate());

			int frameCount = 0;
			while (true) {
//				if ((img = grabber.grab()) != null) {
				if ((myImage[frameCount] = (IplImage) grabber.grab().opaque) != null) {
					// show image on window
//					myImage[frameCount] = (IplImage) img.opaque;
					canvas.showImage(converter.convert(myImage[frameCount]));
					System.out.println("FrameCount: "+frameCount+
							"\nFrameLength: "+segmentLength);
					frameCount++;
					//check end of video
					if(frameCount >= myImage.length){
						outputFileName = PREFIX + (++segmentNum) + ".flv";
						myRecorder = new ICCRecorder(myImage, outputFileName,
								que, FPS);
						writeThread = new Thread(myRecorder);
						writeThread.start();
						segmentNum = segmentNum % MAX_SEGMENTS;
						frameCount = 0;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try{
			s3.end();
			s3.interrupt();
		} catch(Exception e){
			System.err.println(e + ": Unable to save video!");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		InstantCloudCameraRunner iccr = new InstantCloudCameraRunner();
		que = new SharedQueue<>(MAX_SEGMENTS);
		s3 = new S3Uploader(BUCKETNAME, que);
		Thread th = new Thread(iccr);
		s3.start();
		th.start();
	}
}