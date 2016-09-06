package videoSender;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import videoUtility.FileData;
import videoUtility.PerformanceLogger;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;
import videoUtility.VideoSegment;

public class S3Uploader extends S3UserStream {

	private String key;
	private SharedQueue<VideoSegment> _stream;
	private SharedQueue<byte[]> _indexStream;
	private SharedQueue<String> _signalQueue;
	private AmazonS3 s3;
	private PerformanceLogger _logger;

	//-------------------------------------------------------------------------
	//Constructor
	
	public S3Uploader(SharedQueue<VideoSegment> theque){
		_stream = theque;
	}

	//-------------------------------------------------------------------------
	//Run method
	
	public void run() {

		/*
		 * The ProfileCredentialsProvider will return your [default]
		 * credential profile by reading from the credentials file located at
		 * (/Users/username/.aws/credentials).
		 *
		 * AWS IP occasionally changes. This will allow the application to receive
		 * and use new IP without querying DNS again with TTL being 60 
		 */
		java.security.Security.setProperty("networkaddress.cache.ttl", "60");

		AWSCredentials credentials = null;

		Runtime.getRuntime().addShutdownHook(new S3UploaderShutdownHook(this));

		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. " +
							"Please make sure that your credentials file is at the correct " +
							"location (/users/username/.aws/credentials), and is in valid format.",
							e);
		}

		s3 = new AmazonS3Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		s3.setRegion(usWest2);

		System.out.println("===========================================");
		System.out.println("Getting Started with Amazon S3");
		System.out.println("===========================================\n");

		//let ICCRunner know we're ready for the setup file
		_signalQueue.enqueue("S3: Waiting for setup file...");

		synchronized(this){//receive and upload setup file
			String setupfile = null;
			while(setupfile == null){
				try {
					wait();
				} catch (InterruptedException e1) {
					setupfile = _signalQueue.dequeue();
				}
			}
			uploadSetupFile(setupfile);
			System.out.println("S3: Setup file successfully sent to S3");
		}

		while(!isDone || !_stream.isEmpty()){
			ObjectMetadata info = new ObjectMetadata();
			VideoSegment segment;
			
			try { //start uploading video stream
				double timeReceived = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
				segment = _stream.dequeue();
				key = segment.getName();
				info.setContentLength(segment.size());
				System.out.println("Content length: " + info.getContentLength());
//				info.setContentType("video/x-msvideo");
				ByteArrayInputStream bin = new ByteArrayInputStream(segment.getData());
				System.out.println("S3: Uploading file '" + key + "'");
				s3.putObject(new PutObjectRequest(bucketName, key, bin, info));

				logUpload(timeReceived);
				updateIndexFile();

				key = null;

			} catch (AmazonServiceException ase) {
				System.out.println("Caught an AmazonServiceException, which means your request made it "
						+ "to Amazon S3, but was rejected with an error response for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
				System.out.println("HTTP Status Code: " + ase.getStatusCode());
				System.out.println("AWS Error Code:   " + ase.getErrorCode());
				System.out.println("Error Type:       " + ase.getErrorType());
				System.out.println("Request ID:       " + ase.getRequestId());
			} catch (AmazonClientException ace) {
				System.out.println("Caught an AmazonClientException, which means the client encountered "
						+ "a serious internal problem while trying to communicate with S3, "
						+ "such as not being able to access the network.");
				System.out.println("Error Message: " + ace.getMessage());
				System.out.println("Current file to upload: " + key);
			} catch (NoSuchElementException ie){
				//happens during dequeue when program exits
			} catch (Exception e){
				System.err.println(e);
			}
		}//end while

		try{
			_logger.close();
			uploadLogFiles();
		} catch(IOException e){
			System.err.println(e);
		}
		System.out.println("S3 Uploader successfully closed");
	}//end video stream

	//-------------------------------------------------------------------------
	//Public methods
	
	public void delete(String file){
		try{
			s3.deleteObject(new DeleteObjectRequest(bucketName, file));
		} catch(Exception e){
			e.printStackTrace();
		}
		System.out.println("Successfully deleted '"+file+"' from S3");
	}
	
	public void end(){
		if(isDone) return;
		System.out.println("Attempting to close S3 Uploader...");
		while(!_stream.isEmpty()){
			_stream.dequeue();
		}
		isDone = true;
	}
	
	public void setIndexStream(SharedQueue<byte[]> indexStream){
		_indexStream = indexStream;
	}
	
	public void setLogger(PerformanceLogger logger){
		_logger = logger;
	}
	
	public void setKey(String fout){
		key = fout;
	}
	
	public void setSignal(SharedQueue<String> signal){
		_signalQueue = signal;
	}

	//-------------------------------------------------------------------------
	//Private methods
	
	private void logUpload(double timeReceived){
		_logger.logTime();

		double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
		double value = curRunTime - timeReceived;

		try {
			_logger.log(" ");
			_logger.log(value);
			_logger.log("\n");
		} catch (IOException e) {
			System.err.println("S3: Failed to log upload!");
		}
	}

	private void updateIndexFile() throws Exception {
		byte[] data = _indexStream.dequeue();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(data.length);
		s3.putObject(new PutObjectRequest(bucketName,
				FileData.INDEXFILE.print(), inputStream, metadata));
		System.out.println("S3: Uploaded playlist!");
	}

	private void uploadLogFiles() throws IOException {
		String runnerLog = _signalQueue.dequeue();
		String runnerLogPath = _signalQueue.dequeue();
		String s3Log = _logger.getFileName();
		String s3LogPath = _logger.getFilePath();
		s3.putObject(new PutObjectRequest(bucketName, runnerLog, new File(runnerLogPath)));
		s3.putObject(new PutObjectRequest(bucketName, s3Log, new File(s3LogPath)));
	}
	
	private void uploadSetupFile(String setupfile){
		try{
			System.out.println("SETUP: " + setupfile);
			s3.putObject(new PutObjectRequest(bucketName, setupfile, new File(setupfile)));
		} catch(Exception e){
			System.err.println("S3: Failed to upload setup file!");
			e.printStackTrace();
			System.exit(-1);
		}
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

class S3UploaderShutdownHook extends Thread {
	private S3Uploader _uploader;

	public S3UploaderShutdownHook(S3Uploader uploader){
		_uploader = uploader;
	}

	public void run(){
		_uploader.end();
		try {
			_uploader.interrupt();
			_uploader.join();
		} catch (InterruptedException e) {
			System.err.println("S3Uploader end interrupted!");
		}
	}
}