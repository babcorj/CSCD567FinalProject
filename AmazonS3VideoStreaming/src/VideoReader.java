//import java.io.File;
//import java.net.URL;
//import org.bytedeco.javacpp.opencv_videoio.VideoCapture;
import org.bytedeco.javacv.*;
//import org.bytedeco.javacpp.*;
//import org.bytedeco.javacpp.indexer.*;
import static org.bytedeco.javacpp.opencv_core.*;

import java.io.File;
//import static org.bytedeco.javacpp.opencv_imgproc.*;
//import static org.bytedeco.javacpp.opencv_calib3d.*;
//import static org.bytedeco.javacpp.opencv_objdetect.*;

//Taken from: https://github.com/bytedeco/javacv#sample-usage

public class VideoReader implements Runnable {
	//final int INTERVAL=1000;///you may use interval
	IplImage image;
	CanvasFrame canvas = new CanvasFrame("Web Cam");
	public VideoReader() {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
	@Override
	public void run() {
		String inputFileName = "myvideo.mp4";
		File file = new File(inputFileName);
		FFmpegFrameGrabber grabber = null;
//		VideoCapture vcap = new VideoCapture(file.getAbsolutePath());
//		vcap.open(file.getAbsolutePath());
//		if(!vcap.isOpened()){
//			System.err.println("Could not open file");
//			return;
//		}
//		Size mysize = new Size(1024, 1024);
//		Mat imread = new Mat();
		
		
//		https://stackoverflow.com/questions/28494648/video-compression-using-javacv
		try{
			grabber = FFmpegFrameGrabber.createDefault(file.getAbsolutePath()); // 1 for next camera
			grabber.setVideoCodec(13);
			grabber.setFormat("mp4");
//			grabber.start();

			//			int bitrate = grabber.getImageHeight() * grabber.getImageWidth() * (int) grabber.getFrameRate();
//		    if(grabber.getVideoBitrate()>bitrate) {
//		        recorder.setVideoBitrate(bitrate);
//		    }
//		    else {
//		        recorder.setVideoBitrate(grabber.getVideoBitrate());
//		    }
//			recorder = FrameRecorder.createDefault("myvideo.avi", 
//					grabber.getImageWidth(), grabber.getImageHeight());
		}
		catch(Exception e){
			System.err.println(e + ": Unable to load video...");
			e.printStackTrace();
		}
		try {
			Frame img;
			while (true) {
				img = grabber.grabFrame();
//				img = vcap.read();
				if (img != null) {
					// show image on window
					canvas.showImage(img);
					//recorder.record(img);
				} else {
					break;
				}
				//Thread.sleep(INTERVAL);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		VideoReader vr = new VideoReader();
		Thread th = new Thread(vr);
		th.start();
	}
}