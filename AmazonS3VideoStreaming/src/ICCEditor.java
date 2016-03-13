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
	
	//higher ratio means more compression
	public IplImage compress(int ratio){
		IplImage newImage = IplImage.create(ipimg.width()*3/ratio,
				ipimg.height()*3/ratio, ipimg.arrayDepth(), 0);
		int oldlength = ipimg.arraySize();
		int newlength = oldlength/ratio;
		int average = 0;
		for(int i = 0; i < newlength; i++){
			average = getAverage(ipimg, i, ratio);
			newImage.imageData().put(i, (byte) average);
		}
		return newImage;
	}
	
	//Used to get average pixel value from x amount of pixels
	private int getAverage (IplImage ip, int pos, int ratio){
		int sum = 0;
		int coverage = 0;
		int widthstep = ip.width()*3;
		coverage = (int)log2(ratio);
		System.out.println("Log2("+ratio+") = "+coverage);
		try{
			Thread.sleep(10000);
		} catch(Exception e){}
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
	public void setPixelValue(Color color){
		int i = 0,
			value = 255,
			fsize = ipimg.arraySize();
		switch(color){
		case BLUE :
			i = 0;
			value = 255;
			break;
		case GREEN :
			i = 1;
			value = 255;
			break;
		case RED :
			i = 2;
			value = 255;
			break;
		case YELLOW :
			i = 0;
			value = 0;
			break;
		case VIOLET :
			i = 1;
			value = 0;
			break;
		case AQUA :
			i = 2;
			value = 0;
			break;
		case ORANGE :
			setPixelValue(Color.RED);
			setPixelValue(Color.YELLOW);
			return;
		case BLACK :
			setPixelValue(Color.YELLOW);
			setPixelValue(Color.VIOLET);
			setPixelValue(Color.AQUA);
			return;
		case WHITE :
			setPixelValue(Color.BLUE);
			setPixelValue(Color.GREEN);
			setPixelValue(Color.RED);
			return;
		case GREY :
			makeGrey();
			return;
		}
		try{
			for(; i < fsize; i += 3){
				ipimg.imageData().put(i, (byte) value);
			}
		} catch(Exception e){
			e.printStackTrace();
			return;
		}
	}
	
	private void makeGrey(){
		int i = 0,
			j = 0,
		fsize = ipimg.arraySize();
		byte cur = 0;
		double R, G, B, sum;
		
		IplImage newimg = IplImage.create(ipimg.cvSize(), ipimg.depth(), 1);
		try{
			for(; i < fsize; i ++){
				//BLUE
				cur = ipimg.imageData().get(i++);
				B = cur*0.07;
				//GREEN
				cur = ipimg.imageData().get(i++);
				G = cur*0.72;
				//RED
				cur = ipimg.imageData().get(i);
				R = cur*0.21;
				sum = R + B + G;
//				ipimg.imageData().put(i-2, (byte) sum);
//				ipimg.imageData().put(i-1, (byte) sum);
//				ipimg.imageData().put(i-0, (byte) sum);
				newimg.imageData().put(j++, (byte) sum);
			}
			ipimg = newimg;
		} catch(Exception e){
			e.printStackTrace();
			return;
		}
	}
//	0.21 R + 0.72 G + 0.07 B

	public enum Color{
		RED, BLUE, GREEN, YELLOW, VIOLET, AQUA, ORANGE, BLACK, WHITE, GREY;
	}
	
	public static Color[] allColors(){
		Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
				Color.VIOLET, Color.AQUA, Color.ORANGE, Color.GREY};
		return colors;
	}
	
//	public IplImage clone(){
//		int fsize = ipimg.arraySize();
//		IplImage newImg = new IplImage();
//		try{
//			for(int i = 1; i < fsize; i ++){
//				byte data = ipimg.imageData().get(i);
//				newImg.imageData().put(i, data);
//			}
//		} catch(Exception e){
//			e.printStackTrace();
//		}
//		return newImg;
//	}
}

