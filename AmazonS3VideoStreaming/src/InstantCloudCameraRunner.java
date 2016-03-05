//import java.io.File;
//import java.net.URL;
import org.bytedeco.javacv.*;
//import org.bytedeco.javacpp.*;
//import org.bytedeco.javacpp.indexer.*;
import static org.bytedeco.javacpp.opencv_core.*;
//import static org.bytedeco.javacpp.opencv_imgproc.*;
//import static org.bytedeco.javacpp.opencv_calib3d.*;
//import static org.bytedeco.javacpp.opencv_objdetect.*;

//Taken from: https://github.com/bytedeco/javacv#sample-usage

public class InstantCloudCameraRunner implements Runnable {
	//final int INTERVAL=1000;///you may use interval
	IplImage image;
	CanvasFrame canvas = new CanvasFrame("Instant Cloud Camera");
	public InstantCloudCameraRunner() {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
	@Override
	public void run() {
		String outputFileName = "myvideo.flv";
		FrameGrabber grabber = null;
		FFmpegFrameRecorder recorder = null;
//		https://stackoverflow.com/questions/28494648/video-compression-using-javacv
		try{
			grabber = FrameGrabber.createDefault(0); // 1 for next camera
			grabber.start();
			recorder = new FFmpegFrameRecorder(outputFileName,
					grabber.getImageWidth(), grabber.getImageHeight(),
					grabber.getAudioChannels());
			recorder.start();
			recorder.setFrameRate(grabber.getFrameRate());
			recorder.setSampleRate(grabber.getSampleRate());
	        recorder.setFrameRate(30);
	        recorder.setVideoBitrate(10 * 1024 * 1024);
			recorder.setFormat("flv");
		}
		catch(Exception e){
			System.err.println(e + ": Unable to record video...");
			e.printStackTrace();
		}
		try {
			Frame img;
			while (true) {
				img = grabber.grab();
				if (img != null) {
					// show image on window
					canvas.showImage(img);
					recorder.record(img);
				}
				//Thread.sleep(INTERVAL);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try{
			recorder.stop();
		} catch(Exception e){
			System.err.println(e + ": Unable to save video!");
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		InstantCloudCameraRunner gs = new InstantCloudCameraRunner();
		Thread th = new Thread(gs);
		th.start();
	}
}