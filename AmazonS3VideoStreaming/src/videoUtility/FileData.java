package videoUtility;

/**
 * Contains universal data
 * @author ryanj
 *
 */

public enum FileData {
	BUCKET				("icc-videostream-00"),
	INDEXFILE 			("playlist.txt"),
	LOG_DIRECTORY 		("log/"),
	PLAYER_LOG			("VideoPlayerLog.txt"),
	S3UPLOADER_LOG		("S3UploaderLog.txt"),
	S3DOWNLOADER_LOG	("S3DownloaderLog.txt"),
	SCRIPTFILE			("inScript.p"),
	SENDER_LOG			("VideoSenderLog.txt"),
	SETUP_FILE 			("setup.txt"),
	VIDEO_FOLDER		("./videos/"),
	VIDEO_PREFIX 		("myvideo"),
	VIDEO_SUFFIX 		("");
	
	private final String value;
	
	FileData(String str){
		value = str;
	}
	public String print() { return value; }
	public static String print(FileData data){
		return data.print();
	}
}
