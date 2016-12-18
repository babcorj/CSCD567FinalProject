package videoUtility;

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
	public VideoSegment(int index, byte[] data) {
		_data = data;
		_index = index;
	}

	//-------------------------------------------------------------------------
	//GET METHODS
	//-------------------------------------------------------------------------
	public byte[] data(){
		return _data;
	}
	public int getIndex(){
		return _index;
	}
	public int size(){
		return _data.length;
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
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public String toString(){
		return FileData.VIDEO_PREFIX.print() + _index + FileData.VIDEO_SUFFIX.print();
	}
	public static String toString(int index){
		return FileData.VIDEO_PREFIX.print() + index + FileData.VIDEO_SUFFIX.print();
	}
}
