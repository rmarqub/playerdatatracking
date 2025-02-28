package com.playerdatatracking.exceptions.operations;

public class NoPlayerFoundException extends Exception{
	
	
	 public NoPlayerFoundException(String e) {
	        super(e);
	    }
	    public NoPlayerFoundException(String message, Throwable cause) {
	        super(message, cause);
	    }
}
