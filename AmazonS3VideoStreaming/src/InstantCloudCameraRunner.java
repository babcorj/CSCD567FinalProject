package src;

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
	private final int SEGMENT_LENGTH = 8000; //milliseconds
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
		FFmpegFrameRecorder recorder = null;
//		https://stackoverflow.com/questions/28494648/video-compression-using-javacv
		try{
			grabber = FrameGrabber.createDefault(0); // 1 for next camera
//			grabber.setFrameRate(720);
			grabber.start();
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			Frame img;
			IplImage myImage;
//			Mat mat = Mat.cvarrToMat(myImage.asCvMat());
			long start = 0;
			int j = 0;
			while (true) {
				if(recorder == null){
					outputFileName = PREFIX + (++segmentNum) + ".flv";
					recorder = new FFmpegFrameRecorder(outputFileName,
							grabber.getImageWidth(), grabber.getImageHeight(),
							grabber.getAudioChannels());
					recorder.start();
					recorder.setFrameRate(grabber.getFrameRate());
					recorder.setSampleRate(grabber.getSampleRate());
//			        recorder.setFrameRate(10);
//			        recorder.setVideoBitrate(10 * 1024 * 1024);
					recorder.setFormat("flv");
					start = System.currentTimeMillis();
				}
				if ((img = grabber.grab()) != null) {
					// show image on window
					OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
					myImage = (IplImage) img.opaque;
					System.out.println("Width: " + img.imageWidth + ", Height: " + img.imageHeight);
					System.out.println("MyImage: " + myImage.getClass());
					for(int i = j; i < img.imageWidth * img.imageHeight * 3; i += img.imageWidth*3){
						myImage.imageData().put(i, (byte) 0);
					}
					canvas.showImage(converter.convert(myImage));
					recorder.record(img);
				} j+=10;
				//check time of video
				if(System.currentTimeMillis() - start >= SEGMENT_LENGTH){
					try{
						recorder.stop();
					} catch(Exception e){
						System.err.println(e + ": Unable to save video!");
						e.printStackTrace();
					}
					recorder = null;
					segmentNum = segmentNum % MAX_SEGMENTS;
					que.enqueue(outputFileName);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try{
			recorder.stop();
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