import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;

/**
 * The compressor class is responsible for taking in a series of frames
 * (IplImages) and manipulating those images in some way
 */

/**
 * 
 * @author ryanj
 *
 */

public class ICCEditor {
	private IplImage ipimg;
	
	public ICCEditor(IplImage ipframe){
		ipimg = ipframe;
	}
	
	public void setImage(IplImage ipframe){
		ipimg = ipframe;
	}
	
	public void compress(int ratio){
		IplImage newImage = IplImage.create(ipimg.width()*3/ratio,
				ipimg.height()*3/ratio, ipimg.arrayDepth(), 0);
		int oldlength = ipimg.width() * ipimg.height() * 3;
		int newlength = oldlength/ratio;
		for(int i = 0; i < newlength; i++){
			newImage.imageData(i) = 0;
		}
	}
	
	/*
	 * Sets the pixel value of a color or colors
	 * 
	 * @param	colors is the specific color: RED, BLUE, GREEN
	 * @param	value is the value to set the color or colors
	 */
	public void setPixelValue(Color[] colors, int value){
		boolean isRed, isBlue, isGreen;
		for(int i = 0; i < colors.length; i++){
			switch(colors[i]){
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
		}
		
		
		
	}
	
	public void recordVideo(String outputFileName) throws ArrayIndexOutOfBoundsException {
		OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
		FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFileName,
				video[0].width(), video[0].height());
		recorder.setFormat("flv");
		try{
			recorder.start();
			for(int i = 0; i < video.length; i++){
				recorder.record(converter.convert(video[i]));
			}
			recorder.stop();
		} catch(Exception e){
			e.printStackTrace();
		}
		System.out.println("Finished Recording!");
	}

	private enum Color{
		RED, BLUE, GREEN;
	}
}

