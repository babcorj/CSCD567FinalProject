package videoReceiver;

import videoUtility.SharedQueue;
import videoUtility.VideoSegment;

public class VideoStream {

	//-------------------------------------------------------------------------
	//PARAMETERS
	//-------------------------------------------------------------------------
	private final int DEFAULT_SIZE = 100;
	
	private SharedQueue<VideoSegment> _stream;

	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	public VideoStream(){
		_stream = new SharedQueue<>(DEFAULT_SIZE);
	}

	public VideoStream(int size){
		_stream = new SharedQueue<>(size);
	}
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	public void add(VideoSegment video) {
		_stream.enqueue(video);
	}
	public VideoSegment getSegment() {
		return _stream.dequeue();
	}
	public int size(){
		return _stream.size();
	}
}
