package tibbeftp;

import java.io.File;
import java.io.IOException;

/**
 * Manages a fake root!
 * 
 * @author jesper
 */
public class FakeRoot {
    File mRootDir;
    private String mCurDir; // always starts with /
    
    public FakeRoot(String rootDir){
        this( new File(rootDir) );
    }
    public FakeRoot(File rootDir){
        mRootDir = rootDir;
        mCurDir = "/";
    }

    public File[] listFiles() {
        return getRealDir().listFiles();
    }

    /**
     * Returns the relative file/dir relFile from the fCurDir
     */
    public File getFile(String relFile){
        File ret = null;
        if(relFile.startsWith("/"))
            ret = new File(mRootDir,relFile.substring(1));
        else
            ret = new File(getRealDir(),relFile);
        boolean allowed = false;
        try{
            allowed = ret.getCanonicalPath().startsWith(mRootDir.getCanonicalPath());
        }catch(IOException e){
            e.printStackTrace();
        }
        
        if(!allowed){
            ret = null;
        }
        
        return ret;
    }
    /**
     * 
     * @param dir relative path (../fisk, bin etc) or absolute path (starts with /)
     * @return
     */
    public boolean gotoDir(String dir){
        if( !dir.startsWith("/")){ // goto relative path
			if(mCurDir.equals("/"))
				dir = "/" + dir;
			else
				dir = mCurDir + "/" + dir;
		}
		
        File tmp = dir.equals("/") ? mRootDir : new File(mRootDir,dir.substring(1));

		try{
            boolean allowed = tmp.getCanonicalPath().startsWith(mRootDir.getCanonicalPath());
            boolean isDirectory = tmp.isDirectory();
            if(allowed && isDirectory){
                mCurDir = tmp.getCanonicalPath().substring(mRootDir.getCanonicalPath().length());
				mCurDir = mCurDir.replace(File.separatorChar, '/');
				if(!mCurDir.startsWith("/"))
					mCurDir = "/" + mCurDir;
                return true;
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        
        return false;
    }

    /**
     * 
     * @return The current directory (as seen from fakeroot)
     */
    public String getCurDir(){
        if(mCurDir.length() == 0)
            return "/";
        return mCurDir;
    }
    
    /**
     * 
     * @return the physical current directory
     */
    private File getRealDir(){
		String tmp = mCurDir;
		tmp = tmp.substring(1);
		return new File(mRootDir, tmp);
    }

    /**
     * 
     * @return a description of this fakeroot
     */
    public String toString(){
        return "RelDir: " + getCurDir() + "\t AbsDir:" + getRealDir();
    }
}
