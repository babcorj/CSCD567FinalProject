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
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.jcodec.api.FrameGrab8Bit;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.scale.AWTUtil;

public class ICCFrameReader {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private byte[] _data;
	private int _frame;
	private FrameGrab8Bit _grabber;
	private ByteBuffer _buffer;
	private SeekableByteChannel _channel;
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public ICCFrameReader(byte[] data) throws IOException {
		_data = data;
		_buffer = ByteBuffer.wrap(data);
		_channel = new ByteBufferSeekableByteChannel(_buffer);
//		Logger.debug("Channel size = " + _channel.size());
		_frame = 0;
		try {
			_grabber = FrameGrab8Bit.createFrameGrab8Bit(_channel);
		} catch (IOException | JCodecException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getLocalizedMessage());
		}
//		System.out.println("DataSize: " + _data.length);
	}
	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public void close(){
		_data = null;
		_grabber = null;
		_buffer = null;
		try{
			_channel.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	public BufferedImage readImage() throws IOException, JCodecException{
		if(_data == null) return null;
		Picture8Bit pic = _grabber.getNativeFrame();
		return AWTUtil.toBufferedImage8Bit(pic);
	}
	
	public LinkedList<BufferedImage> readAll() throws IOException, JCodecException{
		ByteArrayInputStream instream = new ByteArrayInputStream(_data);	
		LinkedList<BufferedImage> imageList = new LinkedList<>();

		BufferedImage img;
		while((img = readImage()) != null){
			imageList.add(img);
		}
		instream.close();

		return imageList;
	}
}
