package videoReceiver;

import videoUtility.Utility;
import videoUtility.VideoSegment;
import videoUtility.FileData;
import videoUtility.PerformanceLogger;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Scanner;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class S3Downloader extends S3UserStream {

	private AmazonS3 s3;
	private PerformanceLogger _logger;
	private SharedQueue<String> _signalQueue;
	private PlaylistParser parser;
	private VideoStream stream;

	//-------------------------------------------------------------------------
	//Constructor method
	
	public S3Downloader(VideoStream stream) {
		this.stream = stream;
	}

	//-------------------------------------------------------------------------
	//Run method
	
	@Override
	public void run() {
		byte[] videoData = null;
		double currTimeStamp = 0;
		int currIndex, lastIndex = -1;
		int[] currFrameOrder = null;
		String playlist = FileData.INDEXFILE.print();
		String prefix = FileData.VIDEO_PREFIX.print();
		String suffix = FileData.VIDEO_SUFFIX.print();
		VideoSegment videoSegment = null;
		
		Runtime.getRuntime().addShutdownHook(new S3DownloaderShutdownHook(this));
		/*
		 * The ProfileCredentialsProvider will return your [default] credential
		 * profile by reading from the credentials file located at
		 * (/Users/username/.aws/credentials).
		 */

		//---------------------------------------------------------------------
		//Verify Amazon Credentials

		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			System.err.println("Cannot load the credentials from the credential profiles file. "
					+ "\nPlease make sure that your credentials file is at the correct "
					+ "location (/Users/username/.aws/credentials), and is in valid format.");
			System.exit(-1);
		}

		s3 = new AmazonS3Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		s3.setRegion(usWest2);

		System.out.println("===========================================");
		System.out.println("Getting Started with Amazon S3");
		System.out.println("===========================================\n");

		//---------------------------------------------------------------------
		//Retrieve the setup file
		try{
			parseSetupFile();
			
			synchronized(this){
				String str = null;
				while(str == null){
					try {
						wait();
					} catch (InterruptedException e) {
						str = _signalQueue.dequeue();
					}
				}
			}
		} catch(IOException e){
			System.err.println("S3: Failed to retrieve setup file!");
		}

		//---------------------------------------------------------------------
		//Retrieve the playlist

		parser = null;
		while(parser == null) {
			try {	            
				System.out.println("\nAttempting to obtain playlist...");
				parser = new PlaylistParser(getFileData(FileData.INDEXFILE.print()));
			} catch (IOException e) {
				System.err.println("Failed to retrieve playlist!");
				e.printStackTrace();
				Utility.pause(100);
				continue;
			}
		}
		System.out.println("Playlist obtained!");

		//---------------------------------------------------------------------
		//Get video stream

		System.out.println("Obtaining videostream from S3...\n");
		parser.setCurrentIndex(-2);

		while (!isDone) {
			try{
				parser.update(getFileData(playlist));
				currIndex = parser.getCurrentIndex();
				if(currIndex == lastIndex){
					Utility.pause(100);
					continue;
				}
				currTimeStamp = parser.getCurrentTimeStamp();
				currFrameOrder = parser.getCurrentFrameData();
				key = prefix + currIndex + suffix;
				videoData = getFileData(key);
			} catch(IOException ioe){
				System.err.println("Failed to parse playlist!");
				continue;
			} catch(IndexOutOfBoundsException e){
				Utility.pause(100);
				continue;
			} catch (AmazonServiceException ase) {
				System.out.println("Caught an AmazonServiceException, which means your request made it "
						+ "to Amazon S3, but was rejected with an error response for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
				System.out.println("HTTP Status Code: " + ase.getStatusCode());
				System.out.println("AWS Error Code:   " + ase.getErrorCode());
				System.out.println("Error Type:       " + ase.getErrorType());
				System.out.println("Request ID:       " + ase.getRequestId());
				System.out.println("Key:	           '" + key + "'");
				continue;
			} catch (AmazonClientException ace) {
				System.out.println("Caught an AmazonClientException, which means the client encountered "
						+ "a serious internal problem while trying to communicate with S3, "
						+ "such as not being able to access the network.");
				System.out.println("Error Message: " + ace.getMessage());
				continue;
			}

			System.out.println("Downloading file: " + key);

			videoSegment = new VideoSegment(currIndex, currFrameOrder, videoData).setTimeStamp(currTimeStamp);

			stream.add(videoSegment);

    		logDownload(currTimeStamp);
    		
    		lastIndex = currIndex;
		}
		closeEverything();

		System.out.println("S3 Downloader successfully closed");
	}

	//-------------------------------------------------------------------------
	//Public methods
	
	public void end() {
		if(isDone) return;
		System.out.println("Attempting to close S3 Downloader...");
		isDone = true;
	}
	
	public void setKey(String fout) {
		key = fout;
	}
	
	public void setPerformanceLog(PerformanceLogger perfLog){
		_logger = perfLog;
	}
	
	public void setSignal(SharedQueue<String> signal){
		_signalQueue = signal;
	}

	//-------------------------------------------------------------------------
	//Private methods
	
	private void closeEverything(){
		try{
			_logger.close();
		}catch(IOException e){
			System.err.println("Could not close S3 logger!");
		}
	}
	
	private byte[] getSetupFile() throws IOException {
		S3Object object = s3.getObject(new GetObjectRequest(bucketName, FileData.SETUP_FILE.print()));

		DataInputStream instream = new DataInputStream(object.getObjectContent());
		byte[] buffer = new byte[instream.available()];

		ByteArrayOutputStream outstream = new ByteArrayOutputStream();

		int read = 0;
		while ((read = instream.read(buffer)) > 0) {
			outstream.write(buffer, 0, read);
		}
		
		instream.close();
		outstream.close();

		return outstream.toByteArray();
	}
	
	private byte[] getFileData(String key) throws IOException {
		if(key == null) { throw new IOException("Null key"); }

		int alreadyRead = 0,
			read,
			size;
		byte[] buffer;
		S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
		S3ObjectInputStream inputStream = object.getObjectContent();

		size = (int) object.getObjectMetadata().getContentLength();
		buffer = new byte[size];

		while ((read = inputStream.read(buffer, alreadyRead, size)) > 0) {
			alreadyRead += read;
		}
		
		inputStream.close();
		
		return buffer;
	}

	//logs time to send vs. time received
	private void logDownload(double currTimeStamp){
		double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
		double lag = curRunTime - currTimeStamp;
		try {
			_logger.logTime();
			_logger.log(" ");
			_logger.log(lag);
			_logger.log("\n");
		} catch (IOException e) {
			System.err.println("S3: Unable to log download!");
		}
	}
	
	private void parseSetupFile() throws IOException {
		byte[] data = getSetupFile();
//		System.out.println("Data: " + data.length);
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		Scanner sc = new Scanner(in);
		String startTimeMillis = sc.nextLine();
//		System.out.println("Start : " + startTimeMillis);
		String[] specs = sc.nextLine().split(" ");
		sc.close();
		_signalQueue.enqueue(startTimeMillis);
		_signalQueue.enqueue(specs[0]);
		_signalQueue.enqueue(specs[1]);
		_signalQueue.enqueue(specs[2]);
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

class S3DownloaderShutdownHook extends Thread {
	private S3Downloader _downloader;

	public S3DownloaderShutdownHook(S3Downloader downloader){
		_downloader = downloader;
	}

	public void run(){
		_downloader.end();
		try {
			_downloader.interrupt();
			_downloader.join();
		} catch (InterruptedException e) {
			System.err.println(e);
		}
	}
}