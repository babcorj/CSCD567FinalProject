package videoUtility;

import java.awt.Image;
import java.awt.image.BufferedImage;

import org.opencv.core.Mat;

public class VideoSource extends Thread {

	protected boolean isDone;
	protected Mat mat;
	protected String className;
	
	public VideoSource(){
		isDone = false;
		mat = new Mat();
	}
	
	public Image getCurrentFrame() throws NullPointerException {
		int w = mat.cols(),
			h = mat.rows();
		byte[] dat = new byte[w * h * mat.channels()];
		
		BufferedImage img = new BufferedImage(w, h, 
            BufferedImage.TYPE_3BYTE_BGR);
        
        mat.get(0, 0, dat);
        img.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), dat);
        return img.getScaledInstance(img.getWidth()*10, img.getHeight()*10, Image.SCALE_DEFAULT);
	}
	
	public void end(){
		System.out.println("Attempting to close " + className + "...");
		isDone = true;
	}
}
