package videoUtility;

/**
 * @author Ryan Babcock
 * 
 * This class is to be used with OpenCV 3.1.0
 * 
 * Bugs: File used for ImageWriter can be used with PrintWriter
 */

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;


/**
 * 
 * @author Ryan Babcock
 * 
 * The ICCFrameWriter class can be used for multiple purposes. Mainly, it is used
 * to convert a mat, or sequence of mats, into a compressed format and then
 * write to some output. Any device should be able to read the file produced
 * from ICCFrameWriter when using one mat, but in the case where multiple mats are
 * used, the ICCFrameReader class is necessary to read in the subsequent images.
 * <p>
 * By default, the compression type used is JPG. However, this can be modified
 * by using the setCompressionType method.
 * 
 */
public class ICCFrameWriter {

	
	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	
	private int _currentFrame = 0;
	private int[] _frames;
	private ImageWriter _IW;
	private Mat _mat;
	private ByteArrayOutputStream _output;
	private PrintWriter _PW;
	private String _extension = ".jpg";

	
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------

	/**
	 * ICCFrameWriter constructor. Writes mat as image to output.
	 * 
	 * @param mat	 The mat used to print values or write to an image.
	 * @param output Where the image will be written to. Could be an
	 * 				 output stream or file (see ImageWriter).
	 * @throws IOException
	 * @see	ImageWriter
	 */
	public ICCFrameWriter(Mat mat, ByteArrayOutputStream output) {
		_mat = mat;
		_output = output;
		try{			
			setImageOutput(_output);
		} catch(IOException ioe){
			System.err.println(ioe.getMessage());
		}
	}

//-----------------------------------------------------------------------------
//PUBLIC METHODS
//-----------------------------------------------------------------------------
		
	/**
	 * Closes mat and all output streams associated with ICCFrameWriter instance.
	 */
	public void close(){
		if(_PW != null){
			_PW.close();
			_PW = null;
		}
		if(_IW != null){
			_IW.dispose();
			_IW = null;
		}
		if(_mat != null){
			_mat.release();
			_mat = null;
		}
	}

	/**
	 * Checks to see if all resources associated with MatWrtier have been closed.
	 *
	 * @return	True if all streams and mat is closed or released.
	 */
	public boolean isClosed(){
		return (_PW == null)&&(_IW == null)&&(_mat == null);
	}

	/**
	 * Sets up same ICCFrameWriter instance to write new video.
	 * @throws IOException 
	 */
	public void reset() throws IOException{
		setFrames(_frames.length);
//			_IW.dispose();
		_output.reset();
//			_IW = getImageWriter();
	}
	
	/**
	 * Checks the size of the image(s) output.
	 * 
	 * @return	The size of the output, or -1 if output is not a File
	 * or ByteArrayOutputStream.
	 */
	public long size(){
		return _output.size();
	}
	/**
	 * Writes the compressed image to the output.
	 * 
	 * @throws IOException
	 * @see setOutput
	 */
	public void write() throws IOException{
		assert(_mat != null);
		assert(_IW != null);
		assert(_frames != null);
		
		//write image to output
		BufferedImage img = convertToBufferedImage();
		_IW.write(img);
		
//			System.out.println("CURRENT FRAME: " + _currentFrame + "\nFRAMELENGTH: " + _frames.length
//					+ "\nOUTPUT SIZE: " + _output.size());
//			System.out.println(Utility.print(_output.toByteArray()));
		//load _frames with current index value
		if(_currentFrame >= _frames.length){
			System.out.println("Auto reset..");
			this.reset();
		}
		_frames[_currentFrame++] = _output.size();
	}
	
	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	
	/**
	 * @return	Returns the index positions of each frame.
	 */
	public int[] getFrames(){
		return _frames;
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
		_extension = extension;
		if(_IW != null){
			try{
				setImageOutput(_output);
			} catch(NoSuchElementException e){
				throw new NoSuchElementException(e.getMessage());
			}
		}
	}
	
	/**
	 * Sets the output used for writing the compressed image.
	 * 
	 * @param output	The output used for writing the compressed image. Used
	 * 					by ImageWriter class.
	 * @throws IOException
	 * @see	ImageWriter
	 */
	public void setImageOutput(ByteArrayOutputStream output) throws IOException,
			NoSuchElementException {
		try{
			if(_IW != null){
				_IW.dispose();
			}
			_output = output;
			_IW = getImageWriter();
		} catch(IOException ioe){
			throw new IOException("Unable to use ouput");
		} catch(NoSuchElementException e){
			throw new NoSuchElementException(e.getMessage());
		}
	}

	/**
	 * Sets the mat where the data is derived from. Upon method call, the
	 * previous mat that was used by this instance is released.
	 * 
	 * @param mat
	 * @see Mat
	 */
	public void setMat(Mat mat){
		if(_mat != null || !mat.empty()){
			_mat.release();
		}
		_mat = mat;
	}
	
	/**
	 * Sets the number of frames. Used for instances when setup parameters
	 * change (i.e. FPS).
	 */
	public void setFrames(int totalFrames){
		_frames = new int[totalFrames];
		_currentFrame = 0;
	}
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------

	/**
	 * Converts Mat to BufferedImage.
	 * 
	 * @return The compressed image
	 * @throws IOException
	 */
	private BufferedImage convertToBufferedImage() throws IOException {
		MatOfByte matBuffer = new MatOfByte();
		System.out.println("Old: "+(_mat.total() * _mat.elemSize()));
		Imgcodecs.imencode(_extension, _mat, matBuffer);
		InputStream inStream = new ByteArrayInputStream(matBuffer.toArray());
		BufferedImage img = ImageIO.read(inStream);
		inStream.close();
		return img;
	}
	
	public BufferedImage getBufferedImage(Mat _mat) throws IOException {
		MatOfByte matBuffer = new MatOfByte();
		Imgcodecs.imencode(_extension, _mat, matBuffer);
		InputStream inStream = new ByteArrayInputStream(matBuffer.toArray());
		BufferedImage img = ImageIO.read(inStream);
		inStream.close();
		return img;
	}
	
	/**
	 * Creates an ImageWriter used for saving the image created from mat
	 * 
	 * @param 	output		The output used for ImageWriter
	 * @return				The ImageWriter used for saving the image
	 * @throws 	IOException
	 * @see		ImageWriter
	 */
	private ImageWriter getImageWriter() throws IOException,
			NoSuchElementException {
		ImageOutputStream ios = null;
		ImageWriter imageWriter = null;
		Iterator<ImageWriter> imageWriters = null;
		String ext = _extension.toUpperCase().substring(1);
		try{
			imageWriters = ImageIO.getImageWritersByFormatName(ext);
			imageWriter = (ImageWriter) imageWriters.next();
		} catch(NoSuchElementException e){
			throw new NoSuchElementException("Extension invalid!");
		}
		ios = ImageIO.createImageOutputStream(_output);
		imageWriter.setOutput(ios);

		return imageWriter;
	}
}
