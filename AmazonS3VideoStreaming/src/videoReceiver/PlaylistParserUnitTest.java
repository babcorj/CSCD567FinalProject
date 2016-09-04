package videoReceiver;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PlaylistParserUnitTest {

	private final String testfile = "metadata.txt";
	private PlaylistParser parser;

	@Before
	public void setUp() throws Exception {
		parser = new PlaylistParser(testfile);
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testBasic(){
		int cur = parser.getCurrentIndex();
		double timeStamp = parser.getCurrentTimeStamp();
		int[] arr = parser.getCurrentFrameData();
		
		assertEquals(6, cur);
		assertEquals("39.609", Double.toString(timeStamp));
		assertEquals(50, arr.length);
	}
	
	@Test
	public void testSet(){
		parser.setCurrentIndex(2);
		
		int cur = parser.getCurrentIndex();
		double timeStamp = parser.getCurrentTimeStamp();
		int[] arr = parser.getCurrentFrameData();
		
		assertEquals(2, cur);
		assertEquals("19.539", Double.toString(timeStamp));
		assertEquals(50, arr.length);
	}
	
	@Test public void testUpdate(){
		try{
			parser.setCurrentIndex(2);
			parser.update(testfile);
		} catch(IOException ioe){
			ioe.printStackTrace();
		}

		int cur = parser.getCurrentIndex();
		double timeStamp = parser.getCurrentTimeStamp();
		int[] arr = parser.getCurrentFrameData();
		
		assertEquals(3, cur);
		assertEquals("24.556", Double.toString(timeStamp));
		assertEquals(arr.length, 50);		

	}

}
