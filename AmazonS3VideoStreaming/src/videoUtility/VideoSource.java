package videoUtility;

import java.awt.Image;
import java.awt.image.BufferedImage;

import org.opencv.core.Mat;

public class VideoSource extends Thread {

	protected boolean isDone;
	protected ICCMetadata metadata;
	protected Mat mat;
	protected String className;
	
	public VideoSource(){
		isDone = false;
		mat = new Mat();
	}

	public void end(){
		System.out.println("Attempting to close " + className + "...");
		isDone = true;
	}
}
