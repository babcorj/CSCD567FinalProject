package videoReceiver;

import videoUtility.Utility;
import videoUtility.VideoObject;
import videoUtility.PerformanceLogger;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

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

public class S3Downloader extends S3UserStream {

	private final String VIDEO_FOLDER = "videos/";
	private final String INDEXFILE = "StreamIndex.txt";
	private final String SETUPFILE = "setup.txt";
	
	private AmazonS3 s3;
	private PerformanceLogger _logger;
	private SharedQueue<String> _signalQueue;
	private StreamIndexParser parser;
	private String output;
	private String prefix;
	private VideoStream stream;

	//-------------------------------------------------------------------------
	//Constructor method
	
	public S3Downloader(String bucket, String prefix, String output, VideoStream stream) {
		super(bucket);
		this.output = output;
		this.stream = stream;
		this.prefix = prefix;
	}

	//-------------------------------------------------------------------------
	//Run method
	
	@Override
	public void run() {
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
			String tmpI = VIDEO_FOLDER + INDEXFILE + ".tmp";
			File indexTmp = new File(tmpI);
			if(indexTmp.exists()){
				indexTmp.delete();
			}
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
				parser = new StreamIndexParser(prefix, getTemporaryFile(INDEXFILE));
			} catch (IOException e) {
				System.err.println("Failed to retrieve playlist!");
				Utility.pause(100);
				continue;
			}
		}
		System.out.println("Playlist obtained!");

		//---------------------------------------------------------------------
		//Get video stream

		double currTimeStamp = 0;
		File videoFile = null;
		VideoObject videoSegment = null;
		System.out.println("Obtaining videostream from S3...\n");

		while (!isDone) {
			try{
				key = parser.parse(getTemporaryFile(INDEXFILE));
				currTimeStamp = parser.currentTimeStamp();
				videoFile = getTemporaryFile(key);
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

			videoSegment = new VideoObject(videoFile.getAbsolutePath()).setTimeStamp(currTimeStamp);
			stream.add(videoSegment);

    		if(!key.equals(INDEXFILE)){
    			logDownload(currTimeStamp);
    		}
		}
		closeEverything(videoFile);

		System.out.println("S3 Downloader successfully closed");
	}

	//-------------------------------------------------------------------------
	//Public methods
	
	public void end() {
		if(isDone) return;
		System.out.println("Attempting to close S3 Downloader...");
		while(!stream.isEmpty()){
			stream.getFrame();
		}
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
	
	private void closeEverything(File lastTempFile){
		deleteTempFiles(lastTempFile);
		try{
			_logger.close();
		}catch(IOException e){
			System.err.println("Could not close S3 logger!");
		}
	}
	
	//deletes the temporary video file and index file
	private void deleteTempFiles(File videoFile){
		try{
			File toDelete = new File(VIDEO_FOLDER + INDEXFILE + ".tmp");
			toDelete.delete();
			videoFile.delete();
		} catch(Exception e){
			System.err.println("S3: There was an error deleting the temporary files");
		}
	}
	
	private File getSetupFile() throws IOException {
		S3Object object = s3.getObject(new GetObjectRequest(bucketName, SETUPFILE));

		DataInputStream instream = new DataInputStream(object.getObjectContent());
		byte[] buffer = new byte[instream.available()];

		File setupFile = new File(String.format(output + "%s.tmp", key));
		FileOutputStream outstream = new FileOutputStream(setupFile);

		int read = 0;
		while ((read = instream.read(buffer)) > 0) {
			outstream.write(buffer, 0, read);
		}
		
		instream.close();
		outstream.close();

		return setupFile;
	}
	
	private File getTemporaryFile(String key) throws IOException {
		if(key == null) throw new IOException("Null key");
		byte[] buffer;
		File file = null;

		S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));

		DataInputStream instream = new DataInputStream(object.getObjectContent());
		buffer = new byte[instream.available()];

		file = new File(String.format(output + "%s.tmp", key));
		FileOutputStream outstream = new FileOutputStream(file);

		int read = 0;
		while ((read = instream.read(buffer)) > 0) {
			outstream.write(buffer, 0, read);
		}
		
		instream.close();
		outstream.close();

		return file;
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
		File setupFile = getSetupFile();
		BufferedReader br = new BufferedReader(new FileReader(setupFile));
		String startTimeMillis = br.readLine();
		String[] specs = br.readLine().split(" ");
		br.close();
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