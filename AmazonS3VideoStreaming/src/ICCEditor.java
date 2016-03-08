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
	private IplImage[] video;
	
	public ICCEditor(IplImage[] theVideo){
		video = theVideo;
	}
	
	public void compress(){
		
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
	
	public void recordVideo(IplImage[] img, String outputFileName){
		FFmpegFrameRecorder recorder = null;
		OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
		recorder = new FFmpegFrameRecorder(outputFileName,
				grabber.getImageWidth(), grabber.getImageHeight(),
				grabber.getAudioChannels());
		recorder.start();
		recorder.setFrameRate(grabber.getFrameRate());
		recorder.setSampleRate(grabber.getSampleRate());
//        recorder.setFrameRate(10);
//        recorder.setVideoBitrate(10 * 1024 * 1024);
		recorder.setFormat("flv");
		for(int i = 0; i < img.length; i++){
			recorder.record(converter.convert(img[i]));
		}
	}
	
	public enum Color{
		RED, BLUE, GREEN;
	}
}

