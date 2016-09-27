package videoUtility;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * @author Ryan Babcock
 * @param <E>
 * @class CSCD 567
 * @work Homework 3
 */

public class SharedQueue<E> implements Iterable<E> {
	final private LinkedList<E> myList;
	private int maxSize;
	private boolean stop = false;

	// public SharedQueue(){
	// myList = new LinkedList<>();
	// maxSize = 50;
	// }

	public SharedQueue(int size) {
		myList = new LinkedList<>();
		maxSize = size;
	}

	public synchronized E get(int i){
		return myList.get(i);
	}
	
	public synchronized void enqueue(E job) throws IllegalArgumentException {
		if (isFull()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		myList.add(job);
		notify();
	}

	public synchronized E dequeue() throws NoSuchElementException {
		if (isEmpty()) {
			try {
				wait();
			} catch (InterruptedException e) {
				throw new NoSuchElementException("Thread Interrupted!");
			}
		}
		E job = myList.remove();
		notify();
		return job;
	}

	public synchronized boolean isEmpty() {
		return myList.isEmpty();
	}

	public synchronized void stop() {
		stop = true;
		notify();
	}

	public synchronized boolean isStopped() {
		return stop;
	}

	public synchronized int size() {
		return myList.size();
	}

	public synchronized boolean isFull() {
		return myList.size() == maxSize;
	}
	
	@Override
	public Iterator<E> iterator() {
		return new SharedQueueIterator();
	}

	class SharedQueueIterator implements Iterator<E> {

		Iterator<E> _it = myList.iterator();
		
		public E next(){
			return _it.next();
		}
		
		public boolean hasNext(){
			return _it.hasNext();
		}
		public void remove(){
			throw new UnsupportedOperationException("Remove method not supported");
		}
	}
}
