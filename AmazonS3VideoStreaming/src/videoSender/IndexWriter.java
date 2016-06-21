package videoSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;

import videoUtility.SharedQueue;
import videoUtility.VideoObject;

public class IndexWriter implements Runnable {
	private int oldSegment, newSegment, maxSegments;
	private double _startTime;
	private String _folder, indexfile;
	private SharedQueue<String> que;
	private LinkedList<VideoObject> _segCollection;
	
	public IndexWriter(SharedQueue<String> queue, int old, int newest, int max, String folder, String name){
		que = queue;
		oldSegment = old;
		newSegment = newest;
		maxSegments = max;
		_folder = folder;
		indexfile = name;
	}

	public void run(){
		int size = _segCollection.size();
		DecimalFormat formatter = new DecimalFormat("#.000");
		FileWriter fw = null;
		String time;
		try{
			fw = new FileWriter(new File(_folder + indexfile));
			fw.write(maxSegments + "\n" + oldSegment + " " + newSegment + "\n");

			for(int i=0; i < size; i++){
				VideoObject segment = _segCollection.get(i);
				fw.write(segment.getFileName());
				time = formatter.format(((segment.getTimeStamp() - _startTime)/1000));
				fw.write(" " + time + "\n");
			}
			fw.close();
		} catch(IOException e){
			e.printStackTrace();
		}
		que.enqueue(indexfile);
	}
	
	public void setCollection(LinkedList<VideoObject> segmentCollection){
		_segCollection = segmentCollection;
	}
	
	public void setTime(double time){
		_startTime = time;
	}
}
