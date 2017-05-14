package videoUtility;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * 
 * @author Ryan Babcock
 * 
 * This class represents one video segment that is being used within a video
 * stream.
 *
 */
public class VideoSegment {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	/*
	 * @param _data	The video data containing all images in a sequence.
	 * @param _index	The number used to identify this video in the stream.
	 * @param _imglist	Every image within the video segment, taken from data.
	 * @param _header	The header, which contains the timestamp of the video
	 * 					and indeces of every image within _data.
	 */
	private byte[] _data;
	private int _index;
	private LinkedList<BufferedImage> _imglist;
	private VideoSegmentHeader _header;
	
	public VideoSegment(){
		//DVC
	}
	
	/**
	 * Constructor used when header information and video data are separately
	 * known (used by the ICCRunner).
	 * @param index		Index of video segment within video stream.
	 * @param data		The data of every image recorded.
	 * @param header	The header information (incomplete video segment if
	 * 					frame order is null).
	 */
	public VideoSegment(int index, byte[] data, VideoSegmentHeader header) {
		_data = data;
		_index = index;
		_header = header;
		_header.setTimeStamp(System.currentTimeMillis());
	}
	
	/**
	 * Constructor uses data that contains both header and video segment info.
	 * @param index			Index of video segment within video stream.
	 * @param data			The data of every image recorded.
	 * @param headerLength	The length of the header information.
	 */
	public VideoSegment(int index, byte[] data, int headerLength){
		_data = parseVideoData(data, headerLength);
		_index = index;
		_header = new VideoSegmentHeader(parseHeaderData(data, headerLength));
	}

	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	public byte[] data(){
		byte[] data = assembleData();
		return data;
	}
	public VideoSegmentHeader getHeader(){
		return _header;
	}
	public LinkedList<BufferedImage> getImageList(){
		if(_imglist == null){
			_imglist = convertToImageList();
		}
		return _imglist;
	}
	public int getIndex(){
		return _index;
	}
	public long getTimeStamp(){
		return _header.getTimeStamp();
	}
	public long size(){
		return _data.length + _header.size();
	}

	//-------------------------------------------------------------------------
	//SET METHODS
	//-------------------------------------------------------------------------
	public void setIndex(int index){
		_index = index;
	}
	public void setData(byte[] data){
		_data = data;
	}
	public void setHeader(VideoSegmentHeader header){//should be called last
		_header = header;
		_header.setTimeStamp(System.currentTimeMillis());
	}
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public String toString(){
		return FileData.VIDEO_PREFIX + _index + FileData.VIDEO_SUFFIX;
	}
	public static String toString(int index){
		return FileData.VIDEO_PREFIX + index + FileData.VIDEO_SUFFIX;
	}
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------
	private byte[] assembleData(){
		byte[] headerData = _header.data();
		byte[] data = new byte[headerData.length + _data.length];
		//store header data
		System.arraycopy(headerData, 0, data, 0, headerData.length);
		//store video data
		System.arraycopy(_data, 0, data, headerData.length, _data.length);
		
		return data;
	}

	private LinkedList<BufferedImage> convertToImageList(){
		LinkedList<BufferedImage> imglist = null;

		try {
			imglist = ICCFrameReader.readAll(_header.getFrameOrder(), _data);
		} catch (IllegalArgumentException | IOException e) {
			e.printStackTrace();
		}

		return imglist;
	}
	
	private byte[] parseHeaderData(byte[] data, int headerLength){
		return Arrays.copyOfRange(data, 0, headerLength);
	}
	
	private byte[] parseVideoData(byte[] data, int headerLength){
		return Arrays.copyOfRange(data, headerLength, data.length);
	}
}
