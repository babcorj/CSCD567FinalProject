package videoReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class StreamIndexParser {
	
	private String prefix;
	private int maxSegments;
	private int segmentRange = 5;
	private int curr;
	private double currTimeStamp;
	
	public StreamIndexParser(String prefix, File StreamIndex) throws IOException {	

		this.prefix = prefix;
		
		BufferedReader input = new BufferedReader(new FileReader(StreamIndex));
		maxSegments = Integer.parseInt(input.readLine());
		String[] minMax = input.readLine().split(" ");
		input.close();

		curr = Integer.parseInt(minMax[1]) - 1;
	}

	public String parse(File StreamIndex) throws IndexOutOfBoundsException, IOException {

		String filename = null;

		BufferedReader input = new BufferedReader(new FileReader(StreamIndex));

		input.readLine(); //to ignore MAXSEGMENT

		String[] minMax = input.readLine().split(" ");

		int min = Integer.parseInt(minMax[0]);
		int max = Integer.parseInt(minMax[1]);
		if(!validateVideo(min, max)){
			input.close();
			throw new IndexOutOfBoundsException("S3: Out of sync");
		}

		filename = prefix + curr + ".avi";
		
		String str = "";
		while((str = input.readLine()) != null){
			String[] line = str.split(" ");
			if(curr == Integer.parseInt(line[0])){
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
	
	private boolean validateVideo(int min, int max){
		
		if(curr < min) {
			if(min + segmentRange >= maxSegments){
				if(curr > max){
					curr = max;
				}
			} else curr = min;
		}
		else if(curr >= max) {
			if(min + segmentRange >= maxSegments){
				if(curr < min){
					curr = min;
				}
			} else return false;
		}
		else {
			incrementCurrentFrame();
		}
		return true;
	}
	
	private void incrementCurrentFrame(){
		curr = ++curr % maxSegments;
	}
	
}

