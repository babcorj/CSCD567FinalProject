package videoUtility;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;

import javax.imageio.ImageWriter;

import org.jcodec.api.SequenceEncoder8Bit;
import org.jcodec.api.awt.AWTSequenceEncoder8Bit;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Rational;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform8Bit;
import org.opencv.core.Mat;

public class ICCFrameWriter {
	
	private final int VIDEO_SIZE = 300_000;//allocated for buffer

	private int _fps;
	private int _frame;
	private ArrayList<ByteBuffer> _spsList;
	private ArrayList<ByteBuffer> _ppsList;
	private ByteBuffer _sps;
	private ByteBuffer _pps;
	private SequenceEncoder8Bit _enc;
	private Mat _mat;
	private ByteBuffer _buffer;
	private ByteBuffer _out;
	private FramesMP4MuxerTrack _track;
	private H264Encoder _encoder;
	private MP4Muxer _muxer;
	private SeekableByteChannel _channel;
	private Picture8Bit _toEncode;
	private Transform8Bit _transform;
	
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
		_out = ByteBuffer.allocate(1920 * 1080 * 6);
		try {
			initEncoder();
		} catch (NoSuchElementException | IOException e) {
			e.printStackTrace();
		}
		_transform = ColorUtil.getTransform8Bit(
				ColorSpace.RGB, _encoder.getSupportedColorSpaces()[0]);
        _spsList = new ArrayList<ByteBuffer>();
        _ppsList = new ArrayList<ByteBuffer>();
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
	        _track.addSampleEntry(H264Utils.createMOVSampleEntryFromBuffer(_sps, _pps, 4));
			_muxer.writeHeader();
			_channel.close();
//			_enc.finish();
//			_buffer.position(0);
//			Logger.debug("Channel: " + size);
//			_buffer = NIOUtils.fetchFromChannel(_channel, _channel.position());
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
//		try{
//			ColorSpace cs = ColorSpace.GREY;
//			BufferedImage img = matToBufferedImage(_mat);
//			Picture8Bit pic = AWTUtil.fromBufferedImageRGB8Bit(img); 
//			_enc.encodeNativeFrame(AWTUtil.fromBufferedImage8Bit(img, cs));
//			_enc.encodeNativeFrame(pic);
//        }
//	}catch(Exception e){
//		e.printStackTrace();
//	}
		BufferedImage img = matToBufferedImage(_mat);
		Picture8Bit pic = AWTUtil.fromBufferedImageRGB8Bit(img); 

		if(_toEncode == null)
			_toEncode = Picture8Bit.create(pic.getWidth(), pic.getHeight(), _encoder.getSupportedColorSpaces()[0]);

        // Perform conversion
        _transform.transform(pic, _toEncode);

        // Encode image into H.264 frame, the result is stored in '_out' buffer
        _out.clear();
        ByteBuffer result = _encoder.encodeFrame8Bit(_toEncode, _out);

        // Based on the frame above form correct MP4 packet
        _spsList.clear();
        _ppsList.clear();
        H264Utils.wipePSinplace(result, _spsList, _ppsList);
//        H264Utils.wipePSinplace(result, null, null);
        NALUnit nu = NALUnit.read(NIOUtils.from(result.duplicate(), 4));
        H264Utils.encodeMOVPacket(result);

        // We presume there will be only one SPS/PPS pair for now
        if (_sps == null && _spsList.size() != 0)
            _sps = _spsList.get(0);
        if (_pps == null && _ppsList.size() != 0)
            _pps = _ppsList.get(0);

        // Add packet to video track
        int timestamp = 0;
        _track.addFrame(MP4Packet.createMP4Packet(result, timestamp, _fps, 1, _frame,
                false, null, 0, timestamp, 0));

//        timestamp += fps.getDen();
        _frame++;

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
	private BufferedImage matToBufferedImage(Mat in) throws IOException {
		byte[] data = new byte[in.width() * in.height() * (int)in.elemSize()];
		int type;
        BufferedImage out;

        in.get(0, 0, data);

        if(in.channels() == 1){
            type = BufferedImage.TYPE_BYTE_GRAY;
        }
        else{
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        
        out = new BufferedImage(in.width(), in.height(), type);
        out.getRaster().setDataElements(0, 0, in.width(), in.height(), data);

        return out;
	}
	
	/**
	 * Creates SequenceEncoder used for saving the frames
	 * 
	 * @return	
	 * @throws 	IOException
	 * @see		ImageWriter
	 */
	private void initEncoder() throws IOException, NoSuchElementException {
		_frame = 0;
		_buffer = null; _channel = null; _enc = null;
		_buffer = ByteBuffer.allocate(VIDEO_SIZE);
		_channel = new ByteBufferSeekableByteChannel(_buffer);
		_muxer = MP4Muxer.createMP4Muxer(_channel, Brand.MP4);
		_track = _muxer.addTrack(TrackType.VIDEO, _fps);
		_encoder = H264Encoder.createH264Encoder();
		_encoder.setKeyInterval(_fps);

//		_enc = new AWTSequenceEncoder8Bit(_channel, new Rational(1000, _fps));
//		_enc.getEncoder().setKeyInterval(_fps);
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
//		int size = bArr.length;
//		for(int i =0; i< size; i++){
		for(int i =0; i< 1000; i++){
			System.out.print(bArr[i]);
		}
	}
}
