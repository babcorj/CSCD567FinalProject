import org.bytedeco.javacpp.opencv_core.IplImage;

/**
 * The compressor class is responsible for taking in a series of frames
 * (IplImages) and manipulating those images in some way
 */

/**
 * 
 * @author ryanj
 *
 */

public class ICCEditor implements Cloneable {
	private IplImage ipimg;
	
	public ICCEditor() {}
	public ICCEditor(IplImage ipframe){
		ipimg = ipframe;
	}

	public void set(IplImage ipframe){
		ipimg = ipframe;
	}
	
	public IplImage get(){
		return ipimg;
	}
	
	public void compress(int ratio){
		IplImage newImage = IplImage.create(ipimg.width()*3/ratio,
				ipimg.height()*3/ratio, ipimg.arrayDepth(), 0);
		int oldlength = ipimg.width() * ipimg.height() * 3;
		int newlength = oldlength/ratio;
		int average = 0;
		for(int i = 0; i < newlength; i++){
			if(i ==2 )
			average = getAverage(ipimg, i, ratio);
			newImage.imageData().put(i, (byte) average);
		}
	}
	
	//Used to get average pixel value from x amount of pixels
	private int getAverage(IplImage ip, int pos, int ratio){
		int sum = 0;
		int coverage = 0;
		int widthstep = ip.width()*3;
		coverage = (int)log2(ratio);
		for(int i = 0; i < coverage; i += widthstep){
			for(int j = i; j < coverage; j++){
				sum += ip.imageData().get(j);
			}
		}
		return sum;
	}
	
	public double log2(double num){
		return Math.log(num)/Math.log(2.0d);
	}
	
	/*
	 * Sets the pixel value of a color or colors
	 * 
	 * @param	colors is the specific color: RED, BLUE, GREEN
	 * @param	value is the value to set the color or colors
	 */
	public void setPixelValue(Color color, int value){
		boolean isRed, isBlue, isGreen;
		switch(color){
		case RED :
			isRed = true;
			break;
		case BLUE :
			isBlue = true;
			break;
		case GREEN :
			isGreen = true;
			break;
		}
		int fsize = ipimg.arraySize();
		try{
			for(int i = 1; i < fsize/3; i ++){
				ipimg.imageData().put(i, (byte) 0);
			}
		} catch(Exception e){
			e.printStackTrace();
			return;
		}
	}

	public enum Color{
		RED, BLUE, GREEN;
	}
	
	public IplImage clone(){
		int fsize = ipimg.arraySize();
		IplImage newImg = new IplImage();
		try{
			for(int i = 1; i < fsize; i ++){
				byte data = ipimg.imageData().get(i);
				newImg.imageData().put(i, data);
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return newImg;
	}
}

