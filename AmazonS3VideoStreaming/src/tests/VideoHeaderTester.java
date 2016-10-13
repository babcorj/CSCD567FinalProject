package tests;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import videoSender.ICCSetup;
import videoUtility.DisplayFrame;
import videoUtility.ICCFrameWriter;
import videoUtility.Utility;
import videoUtility.VideoSegment;
import videoUtility.VideoSegmentHeader;

public class VideoHeaderTester extends Thread{

	private static final int MAX_VIDEO_INDEX = 10;
	private static final int MAX_SEGMENTS = 5;

	private boolean _isDone = false;
	/*
	 * ICCSetup is used to configure the video recorder settings
	 */
	private static ICCSetup _setup = new ICCSetup()
			.setCompressionRatio(0.4)
			.setDevice(0)
			.setFourCC("MJPG")
			.setFPS(20)
			.setMaxIndex(MAX_VIDEO_INDEX)
			.setMaxSegmentsSaved(MAX_SEGMENTS)
			.setSegmentLength(5);//in seconds
	
	//-------------------------------------------------------------------------
	//Main
	//-------------------------------------------------------------------------
	public static void main(String[] args) {
		/* Used in Run configuration settings */
//			Djava.library.path=/home/pi/Libraries/opencv-3.1.0/build/lib
//			System.out.println(System.getProperty("java.library.path"));
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		VideoHeaderTester test = new VideoHeaderTester();
		test.run();
	}

	//-------------------------------------------------------------------------
	//Run Method
	//-------------------------------------------------------------------------
	/**
	 * The main process utilized by the camera. Records and sends video until
	 * display window is exited.
	 */
	@Override
	public void run() {
		Runtime.getRuntime().addShutdownHook(new VideoHeaderTesterShutdownHook(this));

		int frameCount = 0,
			currentSegment = 0;
		int segmentLength = (int)(_setup.getFPS() * _setup.getSegmentLength());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		DisplayFrame display = this.getDisplay();
		Mat mat = new Mat();
		ICCFrameWriter segmentWriter = new ICCFrameWriter(mat, output);
		VideoCapture grabber = null;
		VideoSegment segment = null;
		VideoSegmentHeader vHeader;

		segmentWriter.setFrames(segmentLength);

		try{
			grabber = _setup.getVideoCapture();
		}
		catch(Exception e){
			System.err.println("ERROR 100: Failed to open recording device");
			System.exit(-1);
		}

		//isDone becomes false when "end()" function is called
		while (!_isDone) {
			try {
				//capture and record video
				if (!grabber.read(mat)) {
					Utility.pause(15);
					continue;
				}
				Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);

				display.setCurrentFrame(this.getCurrentFrame(mat));
				segmentWriter.write();
				frameCount++;

				//loops until end of current video segment
				if(frameCount >= segmentLength){
					vHeader = new VideoSegmentHeader(segmentWriter.getFrames());
					segment = new VideoSegment(currentSegment,
							output.toByteArray(),vHeader);

					System.out.println("HeaderSize: " + vHeader.size()
							+ "\nContent:\n" + vHeader.toString());
					
					//test data
					int size = vHeader.size();
					VideoSegment newVideo = new VideoSegment(currentSegment,
							segment.data(), size);
					VideoSegmentHeader newHeader = newVideo.getHeader();
					
					System.out.println("NEWHeaderSize: " + newHeader.size()
						+ "\nNEWContent:\n" + newHeader.toString());
					
					//start new recording
					currentSegment = (currentSegment + 1) % MAX_VIDEO_INDEX;
					segmentWriter.reset();
					frameCount = 0;
				}
				Utility.pause((long)(1000/_setup.getFPS()));
			}//end try
			catch (Exception e) {
				if(e.getMessage().equals("closed")){
					break;
				}
				e.printStackTrace();
			}
		}//end while
		closeEverything(grabber, segmentWriter);
		System.out.println("Runner successfully closed");
	}

	//-------------------------------------------------------------------------
	//Public methods
	//-------------------------------------------------------------------------
	public void end(){
		if(_isDone) return;
		_isDone = true;
		System.out.println("Attempting to close runner...");
	}
	
	//-------------------------------------------------------------------------
	//Private methods
	//-------------------------------------------------------------------------
	private void closeEverything(VideoCapture grabber, ICCFrameWriter segmentWriter){
		try{
			grabber.release();
			segmentWriter.close();
		} catch(Exception e){
			System.err.println(e);
		}
	}
	
	private Image getCurrentFrame(Mat mat) throws NullPointerException {
		int w = mat.cols(),
			h = mat.rows();
		byte[] dat = new byte[w * h * mat.channels()];

		BufferedImage img = new BufferedImage(w, h, 
				BufferedImage.TYPE_BYTE_GRAY);

		mat.get(0, 0, dat);
		img.getRaster().setDataElements(0, 0, 
				mat.cols(), mat.rows(), dat);
		return img;
	}
	
	private DisplayFrame getDisplay(){
		DisplayFrame display = null;
		try{
			display = new DisplayFrame("Video Header Tester");
			display.setVisible(true);
		} catch (Exception e) {
			System.err.println("ICCR: Failed to open display!");;
		}
		return display;
	}
	
}

//-----------------------------------------------------------------------------
//Shutdown Hook
//-----------------------------------------------------------------------------
/**
* Calls ICCRunner.end() when the class shuts down.
*/
class VideoHeaderTesterShutdownHook extends Thread {
	private VideoHeaderTester _test;

	public VideoHeaderTesterShutdownHook(VideoHeaderTester test){
		_test = test;
	}

	public void run(){
		_test.end();
		try {
			_test.join();
		} catch (InterruptedException e) {
			System.err.println("ICCRunner end interrupted!");
		}
	}
}

