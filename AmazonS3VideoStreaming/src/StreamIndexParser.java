import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class StreamIndexParser {
	
	private String prefix;
	private final int MAXSEGMENTS;
	private int curr;
	
	public StreamIndexParser(String prefix, File StreamIndex) throws IOException {
		
		this.prefix = prefix;
		
		BufferedReader input = new BufferedReader(new FileReader(StreamIndex));		
		
		MAXSEGMENTS = Integer.parseInt(input.readLine());
		
		String[] minMax = input.readLine().split(" ");
		input.close();
		
		curr = Integer.parseInt(minMax[1]);
			
	}
	
	public String parse(File StreamIndex) {
		
		String filename = null;
		
		try {
		
			BufferedReader input = new BufferedReader(new FileReader(StreamIndex));		
		
			input.readLine(); //to ignore MAXSEGMENT
			
			String[] minMax = input.readLine().split(" ");
			input.close();
			
			int min = Integer.parseInt(minMax[0]);
			int max = Integer.parseInt(minMax[1]);
			
			int nextVid = curr++ % MAXSEGMENTS ;
			if(nextVid < min) {
				nextVid = min;
			}
			else if(nextVid > max) {
				//throw new IndexOutOfBoundsException();
			}
			
			filename = prefix + nextVid + ".flv";			
			
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return filename;
	}

}
