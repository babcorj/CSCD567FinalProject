package videoUtility;

/**
 * @author Ryan Babcock
 * 
 * This class is to be used with output from ICCFrameWriter class
 * 
 */

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;

import javax.imageio.ImageIO;

public class ICCFrameReader {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------

	public ICCFrameReader(){
		//empty DVC
	}
	
	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	
	public static BufferedImage readImage(byte[] data) throws IOException{
		if(data != null){
			return ImageIO.read(new ByteArrayInputStream(data));
		}
		return null;
	}

	public static LinkedList<BufferedImage> readAll(int[] frameOrder, byte[] data) throws IOException{
		int prev = 0;
		int totalFrames = frameOrder.length;
		ByteArrayInputStream instream = new ByteArrayInputStream(data);	
		LinkedList<BufferedImage> imageList = new LinkedList<>();

//		System.out.println("Total frames: " + totalFrames);
		for(int i = 0; i < totalFrames; i++){
			int next = frameOrder[i];
//			System.out.println("next: " + next + ", prev: " + prev);
			byte[] frame = new byte[next - prev];
			instream.read(frame, 0, frame.length);
			imageList.add(ICCFrameReader.readImage(frame));
			prev = next;
		}
		instream.close();

		return imageList;
	}
}
