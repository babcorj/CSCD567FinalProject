package videoReceiver;

import videoUtility.SharedQueue;
import videoUtility.VideoObject;

public class VideoStream {

	private final int QUEUE_SIZE = 100;
	
	private SharedQueue<VideoObject> stream;
	private boolean isDone = false;

	public VideoStream(){
		stream = new SharedQueue<>(QUEUE_SIZE);
	}

	public VideoStream(int sizeLimit){
		stream = new SharedQueue<>(sizeLimit);
	}

	public void add(VideoObject video) {
		stream.enqueue(video);
		System.out.println("Video added to stream");
	}

	public VideoObject getFrame() {

		return stream.dequeue();
	}

	public boolean isEmpty() {

		return stream.isEmpty();
	}

	public int size() {

		return stream.size();
	}

	public void done() {
		isDone = true;
	}

	public boolean isDone() {
		return isDone;
	}
}
