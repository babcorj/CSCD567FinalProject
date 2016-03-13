import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

public class VideoStream {

	private Queue<File> stream = new LinkedList<>();
	private boolean isDone = false;

	public synchronized void add(File video) {

		stream.add(video);
		System.out.println("Video added to stream");
		notify();
	}

	public synchronized File getFrame() {

		if (stream.isEmpty()) {
			System.out.println("Stream is empty");
			return null;
		}

		return stream.remove();
	}

	public synchronized boolean isEmpty() {

		return stream.isEmpty();
	}

	public synchronized int size() {

		return stream.size();
	}

	public void done() {
		isDone = true;
	}

	public boolean isDone() {
		return isDone;
	}

	/*
	 * public Queue<Frame> getStream() {
	 * 
	 * return stream; }
	 */
}
