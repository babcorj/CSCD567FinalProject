package videoReceiver;

import videoUtility.SharedQueue;

public class VideoStream {

	private final int QUEUE_SIZE = 100;
	
	private SharedQueue<String> stream;
	private boolean isDone = false;

	public VideoStream(){
		stream = new SharedQueue<>(QUEUE_SIZE);
	}

	public VideoStream(int sizeLimit){
		stream = new SharedQueue<>(sizeLimit);
	}

	public void add(String video) {
		stream.enqueue(video);
		System.out.println("Video added to stream");
	}

	public String getFrame() {

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
