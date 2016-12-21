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

import org.jcodec.api.specific.AVCMP4Adaptor;
import org.jcodec.api.specific.ContainerAdaptor;
import org.jcodec.api.FrameGrab8Bit;
import org.jcodec.api.JCodecException;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.common.JCodecUtil;
import org.jcodec.common.JCodecUtil.Format;
import org.jcodec.common.SeekableDemuxerTrack;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.demuxer.FramesMP4DemuxerTrack;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.scale.AWTUtil;

public class ICCFrameReader {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private byte[] _data;
//	private int _frame;
	private ByteBuffer _buffer;
//	private FrameGrab8Bit _grabber;
//	private FramesMP4DemuxerTrack _track;
	private ContainerAdaptor _decoder;
	private MP4Demuxer _demuxer;
	private SeekableByteChannel _channel;
	private SeekableDemuxerTrack _track;
	private ThreadLocal<byte[][]> buffers;
	
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public ICCFrameReader(byte[] data) throws IOException, JCodecException {
		_data = data;
		_buffer = ByteBuffer.wrap(data);
		_channel = new ByteBufferSeekableByteChannel(_buffer);
		init();
//		Logger.debug("Channel size = " + _channel.size());
//		_frame = 0;
//		try {
//			_grabber = FrameGrab8Bit.createFrameGrab8Bit(_channel);
//		} catch (IOException | JCodecException e) {
//			e.printStackTrace();
//			throw new RuntimeException(e.getLocalizedMessage());
//		}
		
		
//		System.out.println("DataSize: " + _data.length);
	}
	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	
//-----------------------------------------------------------------------------
//PUBLIC METHODS
//-----------------------------------------------------------------------------
	public void close(){
		_data = null;
//		_grabber = null;
		_buffer = null;
		try{
			_channel.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	public BufferedImage readImage() throws IOException, JCodecException{
		if(_data == null) return null;
//		Picture8Bit pic = _grabber.getNativeFrame();
        Packet frame = _track.nextFrame();
        if (frame == null)
            return null;

        Picture8Bit pic =  _decoder.decodeFrame8Bit(frame, getBuffer());
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
	
	
//-----------------------------------------------------------------------------
//PRIVATE METHODS
//-----------------------------------------------------------------------------
	private void decodeLeadingFrames() throws IOException, JCodecException {
        int curFrame = (int) _track.getCurFrame();
        int keyFrame = detectKeyFrame(curFrame);
        _track.gotoFrame(keyFrame);

        Packet frame = _track.nextFrame();
        if (_decoder == null)
            _decoder = new AVCMP4Adaptor(_track.getMeta());

        while (frame.getFrameNo() < curFrame) {
            _decoder.decodeFrame8Bit(frame, getBuffer());
            frame = _track.nextFrame();
        }
        _track.gotoFrame(curFrame);
    }
	
    private int detectKeyFrame(int start) throws IOException {
        int[] seekFrames = _track.getMeta().getSeekFrames();
        if (seekFrames == null)
            return start;
        int prev = seekFrames[0];
        for (int i = 1; i < seekFrames.length; i++) {
            if (seekFrames[i] > start)
                break;
            prev = seekFrames[i];
        }
        return prev;
    }
    
    private byte[][] getBuffer() {
        byte[][] buf = buffers.get();
        if (buf == null) {
            buf = _decoder.allocatePicture8Bit();
            buffers.set(buf);
        }
        return buf;
    }
    
	private void init() throws IOException, JCodecException{
		ByteBuffer header = ByteBuffer.allocate(65536);
    	_channel.read(header);
    	header.flip();
    	MovieBox movie = MP4Util.parseMovieChannel(_channel);
    	_demuxer = new MP4Demuxer(_channel);
    	_track = _demuxer.getVideoTrack();
		decodeLeadingFrames();
		
//        try {
//        	_channel.read(header);
//    		header.flip();
//        	_demuxer = new MP4Demuxer(_channel);
//        	_track = _demuxer.getVideoTrack();
//			decodeLeadingFrames();
//		} catch (IOException | JCodecException e) {
//			// TODO Auto-generated catch block
//			throw new RuntimeException(e.getLocalizedMessage());
//		}
	}
	
	
}
