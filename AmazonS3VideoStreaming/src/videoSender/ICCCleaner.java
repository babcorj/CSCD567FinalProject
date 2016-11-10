package videoSender;

import java.util.LinkedList;

import videoUtility.Utility;

public class ICCCleaner extends Thread {
	boolean _isDone = false;
	S3Uploader s3;
	LinkedList<String> _listDel;
	
	public ICCCleaner(S3Uploader S3) {
		s3 = S3;
		_listDel = new LinkedList<>();
	}
	
	public void add(String s3file){
		_listDel.addLast(s3file);
		this.interrupt();
	}
	
	public void end(){
		_isDone = true;
	}
	
	public void run(){
		String delKey = "";
		while(!_isDone){
			try{
				if(_listDel.isEmpty()){
					try{
						synchronized(this){						
							wait();
						}
						continue;
					} catch(InterruptedException e){}
				}
				delKey = _listDel.removeFirst();
				s3.delete(delKey);
				while(!s3.isDeleted(delKey)){
					Utility.pause(50);
				}
			}catch(Exception e1){
				Utility.pause(50);
				try{
					if(!s3.isDeleted(delKey)){
						_listDel.addFirst(delKey);
					}
				}catch(Exception e2){}
			}
		}
	}
}
