package src;

//import java.io.File;
//import org.bytedeco.javacv.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;
import java.io.File;

//Taken from: https://github.com/bytedeco/javacv#sample-usage

public class VideoReader implements Runnable {
	//final int INTERVAL=1000;///you may use interval
	Frame image = null;
	CanvasFrame canvas = new CanvasFrame("Web Cam");
	FFmpegFrameGrabber grabber = null;
	//CvCapture capture = null; 
	public VideoReader(File file) {
		canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		grabber = new FFmpegFrameGrabber(file.getAbsolutePath());
	}
	@Override
	public void run() {
		
		try {
			grabber.start();
			image = grabber.grab();
		 
		while(image != null) {
			System.out.println("Player got frame " + image.getClass().getName());
			canvas.showImage(image);
			image = grabber.grab();
			Thread.sleep(40);
		}
		} catch (Exception | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		VideoReader vr = new VideoReader(new File("C:\\Users\\Dan\\OneDrive\\Computer Science\\CSCD 567\\CSCD567FinalProject\\AmazonS3VideoStreaming\\video.flv"));
		Thread th = new Thread(vr);
		th.start();
	}
}