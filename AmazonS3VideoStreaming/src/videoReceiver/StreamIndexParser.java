package videoReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class StreamIndexParser {
	
	private String prefix;
	private final int MAXSEGMENTS;
	private int segmentRange = 5;
	private int curr;
	private double currTimeStamp;
	
	public StreamIndexParser(String prefix, File StreamIndex) throws IOException {	

		this.prefix = prefix;
		
		BufferedReader input = new BufferedReader(new FileReader(StreamIndex));				
		MAXSEGMENTS = Integer.parseInt(input.readLine());		
		String[] minMax = input.readLine().split(" ");
		input.close();

		curr = Integer.parseInt(minMax[1]);
	}
	
	public String parse(File StreamIndex) throws IndexOutOfBoundsException, IOException {

		String filename = null;

		BufferedReader input = new BufferedReader(new FileReader(StreamIndex));

		input.readLine(); //to ignore MAXSEGMENT

		String[] minMax = input.readLine().split(" ");

		int min = Integer.parseInt(minMax[0]);
		int max = Integer.parseInt(minMax[1]);
		int nextVid = validateVideo(min, max) - 1;//-1 for error on runner

		filename = prefix + nextVid + ".avi";
		
		String str = "";
		while((str = input.readLine()) != null){
			String[] line = str.split(" ");
			if(nextVid == Integer.parseInt(line[0])){
				currTimeStamp = Double.parseDouble(line[1]);
				break;
			}
		}
		
		input.close();

		return filename;
	}

	public double currentTimeStamp() throws IOException {
		return currTimeStamp;
	}
	
	private int validateVideo(int min, int max)
			throws IndexOutOfBoundsException {
		
		if(curr < min) {
			if(min + segmentRange != max){
				if(curr > max){
					throw new IndexOutOfBoundsException();
				}
			} else curr = min;
		}
		else if(curr > max) {
			if(min + segmentRange != max){
				if(curr < min){
					throw new IndexOutOfBoundsException();
				}
			} else {
				throw new IndexOutOfBoundsException();
			}
		}
		incrementCurrentFrame();
		return curr;
	}
	
	private void incrementCurrentFrame(){
		curr = ++curr % MAXSEGMENTS;
	}
	
//	private void decrementCurrentFrame(){
//		curr--;
//	}
}

