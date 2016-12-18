package videoUtility;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.jcodec.api.PictureWithMetadata8Bit;
import org.jcodec.api.awt.AWTSequenceEncoder8Bit;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Rational;
import org.jcodec.scale.AWTUtil;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

public class ICCFrameWriter {
	
	private final int VIDEO_SIZE = 300_000;//allocated for buffer

	private int _fps;
	private AWTSequenceEncoder8Bit _enc;
	private Mat _mat;
	private ByteBuffer _buffer;
	private SeekableByteChannel _channel;
	private String _extension = ".jpg";

//-----------------------------------------------------------------------------
//PUBLIC METHODS
//-----------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	/**
	 * The standard EVC commonly used for writing a mat or sequence of
	 * mats to an output.
	 * 
	 * @param mat	 The mat used to print values or write to an image.
	 * @param output Where the image will be written to. Could be an
	 * 				 output stream or file (see ImageWriter).
	 * @throws IOException
	 * @see	ImageWriter
	 */
	public ICCFrameWriter(Mat mat, int fps) {
		_fps = fps;
		_mat = mat;
		try {
			initEncoder();
		} catch (NoSuchElementException | IOException e) {
			e.printStackTrace();
		}
	}
	
	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	public ByteBuffer getData(){
		return _buffer;
	}

	
	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	/**
	 * Sets the compression to be used by ImageWriter. The format expects
	 * lowercase characters preceded by a dot, ie. ".jpeg", ".png", ".gif".
	 * By default, this class uses ".jpg".
	 * 
	 * @param extension	The extension type used for compression.
	 * @throws IOException 
	 * @throws NoSuchElementException 
	 * @see ImageWriter
	 */
	public void setCompressionType(String extension) throws NoSuchElementException,
			IOException{
		throw new UnsupportedOperationException("Stuck with H.264");
	}
	
	/**
	 * Sets the mat where the data is derived from.
	 * 
	 * @param mat
	 */
	public void setMat(Mat mat){
		_mat = mat;
	}
	
	/**
	 * Sets the mat where the data is derived from. Upon method call, the
	 * previous mat that was used by this instance is released.
	 * 
	 * @param mat
	 * @see Mat
	 */
	public void setMatClean(Mat mat){
		if(_mat != null || !mat.empty()){
			_mat.release();
		}
		_mat = mat;
	}
	
	//-------------------------------------------------------------------------
	//ORIGINAL
	//-------------------------------------------------------------------------
	/**
	 * Closes mat and all output streams associated with ICCFrameWriter instance.
	 * @throws IOException 
	 */
	public void close() throws IOException{
		if(_enc != null){
			_enc.finish();
			_enc = null;
		}
		if(_mat != null){
			_mat.release();
			_mat = null;
		}
	}

	public void complete(){
		try{
			int size = (int) _channel.position();
			_enc.finish();
			_buffer.position(0);
//			Logger.debug("Channel: " + size);
			trimBuffer(size);
			Logger.debug("SegmentSize: " + (_buffer.array().length));
//			DprintBuffer();
//			_channel.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param videoIndex
	 * @throws IOException
	 */
//	public void exportToFile(int videoIndex) throws IOException{
//		this.exportToFile(FileData.VIDEO_FOLDER.print()
//				+ VideoSegment.toString(videoIndex));
//	}

	/**
	 * @throws IOException 
	 * 
	 */
	public void reset() throws IOException{
		initEncoder();
	}

	/**
	 * Writes the compressed image to the output.
	 * 
	 * @throws IOException
	 * @see setOutput
	 */
	public void write() throws IOException {
		assert(_mat != null);
		assert(_enc != null);
		
		//write image to output
		try{
			PictureWithMetadata8Bit pic;
			BufferedImage img = matToBufferedImage();
			pic = new PictureWithMetadata8Bit(
					AWTUtil.fromBufferedImage8Bit(img, ColorSpace.GREY),
					timeStamp, duration);
			_enc.encodeImage(img);
		}catch(Exception e){
			e.printStackTrace();
		}
//		Logger.debug("Output: " + _channel.position());
		
	}

	
//-----------------------------------------------------------------------------
//PRIVATE METHODS
//-----------------------------------------------------------------------------
	/**
	 * Converts Mat to BufferedImage.
	 * 
	 * @return The compressed image
	 * @throws IOException
	 */
	private BufferedImage matToBufferedImage() throws IOException {
		MatOfByte matBuffer = new MatOfByte();
		Imgcodecs.imencode(_extension, _mat, matBuffer);
		InputStream inStream = new ByteArrayInputStream(matBuffer.toArray());
		BufferedImage img = ImageIO.read(inStream);
		inStream.close();
		return img;
	}
	
	/**
	 * Creates SequenceEncoder used for saving the frames
	 * 
	 * @return	
	 * @throws 	IOException
	 * @see		ImageWriter
	 */
	private void initEncoder() throws IOException,
			NoSuchElementException {
		_buffer = null;
		_buffer = ByteBuffer.allocate(VIDEO_SIZE);
//		for(int i=0; i < 1000; i++){
//			_buffer.array()[i] = 0;
//		}
		_channel = new ByteBufferSeekableByteChannel(_buffer);
		_enc = new AWTSequenceEncoder8Bit(_channel, new Rational(_fps, 1));
	}

	private void trimBuffer(int newSize){
		byte[] bArray = Arrays.copyOf(_buffer.array(), newSize);
		_buffer = null;
		_buffer = ByteBuffer.wrap(bArray);
	}
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------
	private void DprintBuffer(){
		byte[] bArr = _buffer.array();
		int size = bArr.length;
//		for(int i =0; i< size; i++){
		for(int i =0; i< 1000; i++){
			System.out.print(bArr[i]);
		}
	}
}
