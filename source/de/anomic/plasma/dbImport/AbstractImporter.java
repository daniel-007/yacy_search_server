package de.anomic.plasma.dbImport;

import java.io.File;

import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.logging.serverLog;

public abstract class AbstractImporter extends Thread implements dbImporter{

    protected int jobID;
    protected String jobType;
    protected serverLog log;
    protected boolean stopped = false;
    protected boolean paused = false;
    
    protected File importPrimaryPath, importSecondaryPath;
    protected int cacheSize;
    protected long preloadTime;
    
    protected long globalStart = System.currentTimeMillis();
    protected long globalEnd;
    protected long globalPauseLast;
    protected long globalPauseDuration;
    protected String error;
    protected plasmaWordIndex wi;
    
    public AbstractImporter(plasmaWordIndex wi) {
        //super(theSb.dbImportManager.runningJobs,"");
        this.wi = wi;
    }
    
    public String getError() {
        return this.error;
    }    
    
    public void init(File thePrimaryPath, File theSecondaryPath) {
        if (thePrimaryPath == null) throw new NullPointerException("The Primary Import path must not be null.");
        if (theSecondaryPath == null) throw new NullPointerException("The Secondary Import path must not be null.");
        this.importPrimaryPath = thePrimaryPath;
        this.importSecondaryPath = theSecondaryPath;
        
        // getting a job id from the import manager
        //this.jobID = this.sb.dbImportManager.getJobID();
        
        // initializing the logger and setting a more verbose thread name
        this.log = new serverLog("IMPORT_" + this.jobType + "_" + this.jobID);
        this.setName("IMPORT_" + this.jobType /*+ "_" + this.sb.dbImportManager.getJobID()*/);
    }
    
    public void startIt() {
        this.start();
    }
    
    public void stopIt() throws InterruptedException {
        this.stopped = true;
        this.continueIt();
        this.join();
    }
    
    public void pauseIt() {
        synchronized(this) {
        	this.globalPauseLast = System.currentTimeMillis();
            this.paused = true;
        }
    }
    
    public void continueIt() {
        synchronized(this) {
            if (this.paused) {
            	this.globalPauseDuration += System.currentTimeMillis()-this.globalPauseLast;
                this.paused = false;
                this.notifyAll();
            }
        }
    }
    
    public boolean isPaused() {
        synchronized(this) {
            return this.paused;
        }
    }
    
    protected boolean isAborted() {
        synchronized(this) {
            if (this.paused) {
                try {
                    this.wait();
                }
                catch (InterruptedException e){}
            }
        }
        
        return (this.stopped) || Thread.currentThread().isInterrupted();
    }    
    
    public boolean isStopped() {
        return !this.isAlive();
    }
    
    public int getJobID() {
        return this.jobID;
    }
    
    public long getTotalRuntime() {
        return (this.globalEnd == 0)?System.currentTimeMillis()-(this.globalStart+this.globalPauseDuration):this.globalEnd-(this.globalStart+this.globalPauseDuration);
    }    
    
    public long getElapsedTime() {
    	if(this.paused) {
    		this.globalPauseDuration += System.currentTimeMillis()-this.globalPauseLast;
        	this.globalPauseLast = System.currentTimeMillis();
    	}
        return isStopped()?this.globalEnd-(this.globalStart+this.globalPauseDuration):System.currentTimeMillis()-(this.globalStart+this.globalPauseDuration);
    }

    public String getJobType() {
        return this.jobType;
    }
    
    public File getPrimaryImportPath() {
        return this.importPrimaryPath;
    }
    public File getSecondaryImportPath() {
        return this.importSecondaryPath;
    }
    
    public abstract long getEstimatedTime();
    public abstract String getJobName();
    public abstract int getProcessingStatusPercent();

}
