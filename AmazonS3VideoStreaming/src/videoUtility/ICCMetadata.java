package videoUtility;

import java.io.FileWriter;
import java.io.IOException;
//import java.io.IOException;
import java.text.DecimalFormat;

//import videoReceiver.PlaylistParser;

/**
 * @author	Ryan Babcock
 **/

/*
 * ICCMetadata is a datastructure used by the videoUtility package within the
 * ICCProject. This class provides a datastructure focused on IO operations
 * for the playlist. It holds information about the current videos available
 * to play and information about their individual frame sizes.
 * 
 * @param	_maxVideoIndex	The largest index #
 * @param	_maxSegments	The max # of segments saved
 * @param	_firstVideoPos	The index # of the earliest video recorded
 * @param	_lastVideoPos	The index # of the latest video recorded
 * @param	_frameList		Keeps a record of the available videos and 
 * 							information about their frame location
 * @param	_filename		Either the name of the file being written to or
 * 							read from
 */
public class ICCMetadata {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private int _maxVideoIndex,
				_maxSegments,
				_firstVideoPos,
				_lastVideoPos,
				_size;
	private long _startTime;
	private SharedQueue<VideoSegment> _videoDataList;
	
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public ICCMetadata(int maxSegments, int maxIndex){
		_maxSegments = maxSegments;
		_maxVideoIndex = maxIndex;
		init();
	}
//	public ICCMetadata(String filename) {
//		parseFile(filename);
//	}
	
	//-------------------------------------------------------------------------
	//GETS
	//-------------------------------------------------------------------------
	public int getMaxVideoCount(){
		return _maxVideoIndex;
	}
	public int getFirstVideoIndex(){
		return _firstVideoPos;
	}
	public int getLastVideoIndex(){
		return _lastVideoPos;
	}
	public VideoSegment getVideoInfo(int i){
		return _videoDataList.get(i);
	}
	
	//-------------------------------------------------------------------------
	//SETS
	//-------------------------------------------------------------------------
	
	public void setStartTime(long time){
		_startTime = time;
	}

	//-------------------------------------------------------------------------
	//TOSTRING
	//-------------------------------------------------------------------------
	public String toString(){
		StringBuilder sbuilder = new StringBuilder();

		sbuilder.append(printSegmentOrder());
		sbuilder.append(printSegmentData());
		sbuilder.append(printSegmentFrameData());
		
		return sbuilder.toString();
	}
	
	//-------------------------------------------------------------------------
	//EQUALS
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//COMPARETO
	//-------------------------------------------------------------------------
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------

	public void update(VideoSegment segmentData){
		if(_size >= _maxSegments){
			_videoDataList.dequeue();
			_videoDataList.enqueue(segmentData);
			_firstVideoPos = ++_firstVideoPos % _maxVideoIndex;
			_lastVideoPos = ++_lastVideoPos % _maxVideoIndex;
		}
		else {
			_videoDataList.enqueue(segmentData);
			_lastVideoPos = ++_lastVideoPos % _maxVideoIndex;
			_size++;
		}
	}
	
	/**
	 * Exports contents to byte array
	 * 
	 * @param data	The formatted ICCMetadata
	 */
	public byte[] export(){
		byte[] data = null;
		
		return data;
	}
	
	public void exportTo(String filename) throws IOException{
		try{
			FileWriter fw = new FileWriter(filename);
			fw.write(this.toString());
			fw.close();
		} catch(IOException ioe){
			throw new IOException("Unable to write metadata to '" + filename + "'.");
		}
	}

	/**
	 * 
	 * @throws IOException
	 */
	public void exportToFile() throws IOException{
		this.exportTo(FileData.VIDEO_FOLDER.print()
				+ FileData.INDEXFILE.print());
	}
	
	
	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------

	private void init(){
		_firstVideoPos = 0;
		_lastVideoPos = -1;
		_size = 0;
		_videoDataList = new SharedQueue<>(_maxSegments);
	}
	
//	private void parseFile(String filename) throws IOException{
//		PlaylistParser parser = new PlaylistParser(filename);
//		_maxVideoIndex = parser.getMaxVideoIndex();
//		_maxSegments,
//		_firstVideoPos,
//		_lastVideoPos,
//		_size;
//		_startTime;
//		_videoDataList;
//	}
	
	private String printSegmentOrder(){
		StringBuilder sb = new StringBuilder();
		sb.append("<SegmentOrder>\n");
		sb.append(Integer.toString(_maxSegments) + " ");
		sb.append(Integer.toString(_maxVideoIndex) + " ");
		sb.append(Integer.toString(_firstVideoPos) + " ");
		sb.append(Integer.toString(_lastVideoPos) + "\n");
		sb.append("</SegmentOrder>\n");
		return sb.toString();
	}
	
	private String printSegmentData(){
		DecimalFormat formatter = new DecimalFormat("#.000");
		String time = "";
		StringBuilder sb = new StringBuilder();
		
		sb.append("<SegmentInfo>\n");
		for(VideoSegment v : _videoDataList){
			time = formatter.format(((v.getTimeStamp() - _startTime)/1000));
			sb.append(v.getIndex() + ":" + time + "\n");
		}
		sb.append("</SegmentInfo>\n");
		return sb.toString();
	}
	
	private String printSegmentFrameData(){
		StringBuilder sb = new StringBuilder();
		
		sb.append("<SegmentFrameData>\n");
		
		for(VideoSegment v : _videoDataList){
			int i, size;
			int[] x = v.getFrameOrder();
			sb.append(v.getIndex() + ":<");

			for(i = 0, size = x.length-1; i < size; i++){
				sb.append(Integer.toString(x[i]) + " ");
			}
			sb.append(Integer.toString(x[i]) + "/>\n");
		}
		sb.append("</SegmentFrameData>\n");
		return sb.toString();
	}
	
}
