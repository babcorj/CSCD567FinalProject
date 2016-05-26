package videoSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import videoUtility.SharedQueue;

public class IndexWriter implements Runnable {
	private int oldSegment, newSegment, maxSegments;
	private String _folder, indexfile;
	private SharedQueue<String> que;
	
	public IndexWriter(SharedQueue<String> queue, int old, int newest, int max, String folder, String name){
		que = queue;
		oldSegment = old;
		newSegment = newest;
		maxSegments = max;
		_folder = folder;
		indexfile = name;
	}

	public void run(){
		FileWriter fw = null;
		try{
			fw = new FileWriter(new File(_folder + indexfile));
			fw.write(maxSegments + "\n" + oldSegment + " " + newSegment);
			fw.close();
		} catch(IOException e){
			e.printStackTrace();
		}
		que.enqueue(indexfile);
	}
}
