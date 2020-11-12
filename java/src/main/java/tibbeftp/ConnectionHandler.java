package tibbeftp;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 * One instance of this class handles one ftp connection
 *
 * @author Jesper Tiberg
 */
public class ConnectionHandler extends Thread {

    public static final boolean allowAnyDataPortIp = "true".equals(System.getenv("PASV_PROMISCUOUS"));

    enum TransferMode {
        TEXT, BINARY
    }

    private final Logger logger;
    private long mStartTime = 0;
    private final Socket mSocket;
    private final OutputStream mSockOut;
    private final InputStream sockIn;
    private boolean clientQuit = false;

    private FakeRoot mFakeRoot = null;
    private Account mAccount = null;

    private MyFTP mMyFTP = null;
    private final String mMyIP;
    private boolean mPasv = false;
    private int mDataPort = -1;
    private ServerSocket mServerSocketData = null;
    private long mRest = 0;
    private String mRnfr = null;

    private boolean mActive = false;
    private String mPortIP = null;
    private int mPortPort;
    private boolean loggedIn = false;
    private String username = null;
    private TransferMode transferMode = TransferMode.TEXT;

    private String mCurrentEncoding = "UTF-8";
    private static final int SESSION_TIMEOUT_MILLIS = 12 * 60 * 60000; // timeout 12 hours

    public ConnectionHandler(ThreadGroup tg, MyFTP myftp, Socket s) throws IOException {
        super(tg, "Connection_" + s.getInetAddress());
        logger = new Logger(s.getInetAddress().getHostAddress());

        mStartTime = System.currentTimeMillis();
        mMyFTP = myftp;
        mSocket = s;
        mSocket.setSoTimeout(SESSION_TIMEOUT_MILLIS);

        // Determine this IP address
        mMyIP = Utils.getMyIpString(s);
        sockIn = mSocket.getInputStream();
        mSockOut = mSocket.getOutputStream();

        logger.info("Connection from " + s.getInetAddress() + " server IP is " + mMyIP);
    }

    public String oneLineInfo() {
        String ret = new Date(mStartTime) + "\t" + mSocket.getInetAddress().getHostAddress() + "\t" + mMyIP + "\t";
        if (mServerSocketData != null) {
            ret += "ssport:" + mServerSocketData.getLocalPort() + "\t";
        }
        if (mAccount != null) {
            ret += mAccount.getName() + " @ " + mFakeRoot.mRootDir.getAbsoluteFile();
        }
        return ret;
    }

    private void send(String str) throws IOException {
        logger.sendCommand(str);
        mSockOut.write((str + "\r\n").getBytes(mCurrentEncoding));
    }

    /**
     * Set port
     */
    private boolean port(String arg) throws IOException {
        try {
            StringTokenizer st = new StringTokenizer(arg, ",");
            mPortIP = st.nextToken() + "." + st.nextToken() + "." + st.nextToken() + "." + st.nextToken();
            mPortPort = Integer.parseInt(st.nextToken()) * 256 + Integer.parseInt(st.nextToken());
            mActive = true;
            mPasv = false;
            send("220 PORT command successfull " + mPortIP + ":" + mPortPort);
        } catch (NumberFormatException e) {
            logger.error(e);
            send("501 Illegal PORT command!");
            return false;
        }

        return true;
    }

    /**
     * @param startR start of range for serversocket
     * @param endR   end of range
     * @return true if serversocket exists or one was created successfully
     * @throws IOException if unable to create a serversocket
     */
    private boolean useExistingOrCreateNewServerSocket(int startR, int endR) throws IOException {
        mPasv = true;
        mActive = false;

        if (mServerSocketData != null) { // Serversocket already exists
            return true;
        } else { // Create a serversocket
            // Is the range not set (0), then just take a port
            if (startR == 0) {
                mServerSocketData = new ServerSocket();
            } else { // otherwise, scan for a free port in the range
                for (int i = startR; i <= endR; i++) {
                    try {
                        mServerSocketData = new ServerSocket(i);
                        break;
                    } catch (IOException e) {
                        // port probably in use, try the next in the range
                    }
                }
            }
            // Were we successful in creating a server socket?
            if (mServerSocketData != null) {
                mDataPort = mServerSocketData.getLocalPort();
                mServerSocketData.setSoTimeout(10000);
                return true;
            }
        }
        return false;
    }

    /**
     * Set passive or active mode
     */
    private void pasv(int startR, int endR) throws IOException {
        if (useExistingOrCreateNewServerSocket(startR, endR)) {
            send("227 Entering Passive Mode (" + Utils.ipAndPortToFTPformat(mMyIP, mDataPort) + ").");
        } else {
            Logger.logToConsole("Critical: Unable to open serversocket for " + username + " (perhaps some client is misbehaving, or a larger range needs to be allocated). Infosys output: " + mMyFTP.getSysInfo());
            send("550 Could not open serversocket");
        }
    }

    /**
     * Opens a connection, either
     */
    private Socket openConnection() throws IOException {
        if (!mPasv && !mActive) {
            send("425 Unable to build data connection! Neither active or passive chosen");
            throw new IOException("Neither passive or active mode set");
        }

        Socket s = null;
        try {
            if (mActive) {
                s = new Socket(mPortIP, mPortPort);
            } else if (mPasv) {
                s = mServerSocketData.accept();
                boolean dataIpSameAsCommandPortIp = s.getInetAddress().equals(mSocket.getInetAddress());
                if (!allowAnyDataPortIp && !dataIpSameAsCommandPortIp) {
                    logger.warning("Illegal data connection: Source IP mismatch: " + s.getInetAddress());
                    s.close();
                }
            }
        } finally {
            if (mServerSocketData != null) {
                mServerSocketData.close();
                mServerSocketData = null;
            }
            mPasv = false;
            mActive = false;
        }
        return s;
    }

    private String getFileInfoLineForList(File f) {
        Date fDate = new Date(f.lastModified());

        // File access permissions
        String fap = (f.canRead() ? "r" : "-")
                + (f.canWrite() ? "w" : "-");
        // If it's a pre-java1.6 canExecute does not exist
        try {
            fap += f.canExecute() ? "x" : "-";
        } catch (NoSuchMethodError e) {
            fap += "x";
        }

        String ret
                = (f.isDirectory() ? "d" : "-") + fap + fap + fap + "   "
                + (f.isDirectory() ? "2" : "1") + " NOBODY  NOBODY "
                + f.length() + " " + Utils.dateToFTPTimeString(fDate) + " " + f.getName();

        return ret + "\r\n";
    }

    /**
     * Issue a list (files) command
     */
    private boolean list() throws IOException {
        try {
            Socket s = openConnection();
            OutputStream dataOut = s.getOutputStream();
            send("150 OK Here comes the file!");

            String tmp = getFileInfoLineForList(mFakeRoot.getFile("."));
            dataOut.write(tmp.getBytes(mCurrentEncoding));
            // List ".." only when not in root dir
            if (!mFakeRoot.getCurDir().equals("/")) {
                tmp = getFileInfoLineForList(mFakeRoot.getFile(".."));
                dataOut.write(tmp.getBytes(mCurrentEncoding));
            }

            // List all files in the directory
            File[] files = mFakeRoot.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    tmp = getFileInfoLineForList(files[i]);
                    dataOut.write(tmp.getBytes(mCurrentEncoding));
                }
            }
            s.close();

            send("226 Transfer complete.");
        } catch (IOException e) {
            send("425 Unable to build data connection!");
            return false;
        }

        return true;
    }

    /**
     * Issue a retr (download) command
     */
    private boolean retr(String fil) throws IOException {
        File f = mFakeRoot.getFile(fil);
        if (f == null) {
            send("550 : Permission denied");
            return false;
        }
        if (!f.isFile()) {
            send("550 : Not a file!");
            return false;
        }

        try {
            Socket s = openConnection();
            long startT = System.currentTimeMillis();
            OutputStream out = s.getOutputStream();
            send("150 Opening data connection for file " + fil + " (" + f.length() + " bytes)");
            logger.notify("GET " + f + " via " + s);

            long totalData = 0;
            FileInputStream fin = new FileInputStream(f);
            fin.skip(mRest);
            mRest = 0;
            byte[] buffer = new byte[1024 * 100];
            int read;
            while ((read = fin.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalData += read;
            }
            fin.close();
            s.close();

            double kBps = totalData / 1.024 / (System.currentTimeMillis() - startT);
            send("226 Transfer complete - " + Utils.maxDec(kBps, 1) + " KB/s");
        } catch (IOException e) {
            logger.error(e);
            send("425 Unable to build data connection! " + e);
            return false;
        }

        return true;
    }

    /**
     * Issue a stor (upload) command
     */
    private boolean stor(String fil) throws IOException {
        File f = mFakeRoot.getFile(fil);
        if (f == null) {
            send("550 : Permission denied");
            return false;
        }

        try {
            Socket s = openConnection();
            long startT = System.currentTimeMillis();
            InputStream in = s.getInputStream();
            send("150 Opening " + transferMode + " mode data connection for file " + fil);
            logger.notify("PUT " + f + " via " + s);

            boolean append = mRest == -1 || mRest == f.length();

            if (mRest > 0 && append) {
                logger.notify("WARNING!!! rest=" + mRest + ", flen=" + f.length());
            }

            FileOutputStream fout = new FileOutputStream(f.getPath(), append);
            mRest = 0;

            long totalData = 0;
            if (transferMode == TransferMode.BINARY) {
                byte[] buffer = new byte[1024 * 100];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    fout.write(buffer, 0, read);
                    totalData += read;
                }
            } else {
                BufferedReader lineReader = new BufferedReader(new InputStreamReader(in));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(fout));
                String line;
                while ((line = lineReader.readLine()) != null) {
                    out.println(line);
                    totalData += line.length() + 1;
                }
                out.close();
            }
            fout.close();
            s.close();

            double kBps = totalData / 1.024 / (System.currentTimeMillis() - startT);
            send("226 Transfer complete - " + Utils.maxDec(kBps, 1) + " KB/s");
        } catch (IOException e) {
            logger.error(e);
            send("426 Transfer aborted " + e);
            return false;
        }

        return true;
    }

    /**
     * Issue a cwd (change working directory) command
     */
    private boolean cwd(String name) throws IOException {
        boolean ok = mFakeRoot.gotoDir(name);
        if (!ok) {
            send("550 " + name + ": No such file or directory");
            //send("550 "+name+": Permission denied. ");
            return false;
        } else {
            send("250 CWD command successful.");
        }
        return true;
    }

    /**
     * Issue a mkdir command
     */
    private boolean mkdir(String name) throws IOException {
        File f = mFakeRoot.getFile(name);
        logger.notify("mkdir " + f);
        if (f == null) {
            send("550 " + name + ": Permission denied");
            return false;
        }
        if (!f.mkdir()) {
            send("550 " + name + ": Unable to create directory");
            return false;
        }

        send("257 \"" + name + "\" - Directory created successfully");
        return true;
    }

    /**
     * Issue a size of file command
     */
    private boolean size(String name) throws IOException {
        File f = mFakeRoot.getFile(name);
        if (f == null) {
            send("550 " + name + ": Permission denied");
            return false;
        }
        if (!f.isFile()) {
            send("550 Not a file!");
            return false;
        }
        send("213 " + f.length());
        return true;
    }

    /**
     * Remove a file
     */
    private boolean dele(String name) throws IOException {
        File f = mFakeRoot.getFile(name);
        logger.notify("Delete " + f);
        if (f == null) {
            send("550 " + name + ": Permission denied");
            return false;
        }
        if (!f.isFile()) {
            send("550 Not a file!");
            return false;
        }
        if (!f.delete()) {
            send("550 File remove failed, you so stupid! " + f);
        } else {
            send("250 File gone!");
        }
        return true;
    }

    /**
     * Remove a directory
     */
    private boolean rm(String name) throws IOException {
        File f = mFakeRoot.getFile(name);
        logger.notify("Delete " + f);
        if (f == null) {
            send("550 " + name + ": Permission denied");
            return false;
        }
        if (!f.isDirectory()) {
            send("550 Not a directory!");
            return false;
        }
        if (!f.delete()) {
            int numFilesInDir = f.list().length;
            if (numFilesInDir > 0) {
                send("550 Directory remove failed. There are " + numFilesInDir + " files/dirs in there");
            } else {
                send("550 Directory remove failed, awfully sorry about that :-(");
            }
        } else {
            send("250 Directory gone!");
        }
        return true;
    }

    /**
     * Issue a size of file command
     */
    private boolean mdtm(String name) throws IOException {
        File f = mFakeRoot.getFile(name);
        if (f == null) {
            send("550 " + name + ": Permission denied");
            return false;
        }
        if (!f.exists()) {
            send("550 " + name + ": No such file or directory");
            return false;
        }

        long fTime = f.lastModified();
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String tStr = df.format(new Date(fTime));
        send("213 " + tStr);

        return true;
    }

    /**
     * Read a line from the connected socket
     *
     * @return
     * @throws java.io.IOException
     */
    private String readLine() throws IOException {
        byte[] tmp = new byte[1024];
        int len;
        for (len = 0; len < tmp.length; len++) {
            int next = sockIn.read();
            if (next == -1) {
                return null;
            }
            tmp[len] = (byte) (next & 0xff);
            if (next == 10 || next == 13) {
                break;
            }
        }

        String ret = new String(tmp, 0, len, mCurrentEncoding).trim();
        return ret;
    }

    /**
     * The actual thread
     */
    @Override
    public void run() {
        try {
            mPasv = false;
            mActive = false;

            send("220 Welcome to the TibbeFTP v" + MyFTP.VERSION);

            String cmdLine;
            while ((cmdLine = readLine()) != null) {
                mSocket.setSoTimeout(SESSION_TIMEOUT_MILLIS);
                logger.recvCommand(cmdLine);

                StringTokenizer st = new StringTokenizer(cmdLine);
                if (!st.hasMoreTokens()) {
                    continue;
                }
                String cmd = st.nextToken().toUpperCase();
                String arg = st.hasMoreTokens() ? st.nextToken("").trim() : null;

                if (!handleCommand(cmd, arg)) {
                    if (!loggedIn) {
                        send("530 Not logged in");
                    } else if (cmd.equals("PORT")) {
                        port(arg);
                    } else if (cmd.equals("PASV")) {
                        pasv(MyFTP.PASV_RANGE_MIN, MyFTP.PASV_RANGE_MAX);
                        mSocket.setSoTimeout(10000);
                    } else if (cmd.equals("MKD") && arg != null) {
                        mkdir(arg);
                    } else if (cmd.equals("CWD") && arg != null) {
                        cwd(arg);
                    } else if (cmd.equals("CDUP") && arg != null) {
                        cwd("..");
                    } else if (cmd.equals("LIST") || cmd.equals("NLST")) {
                        list();
                    } else if (cmd.equals("SIZE") && arg != null) {
                        size(arg);
                    } else if (cmd.equals("DELE") && arg != null) {
                        dele(arg);
                    } else if (cmd.equals("RM") && arg != null) {
                        rm(arg);
                    } else if (cmd.equals("RMD") && arg != null) {
                        rm(arg);
                    } else if (cmd.equals("MDTM") && arg != null) {
                        mdtm(arg);
                    } else if (cmd.equals("RNFR") && arg != null) {
                        mRnfr = arg;
                        send("350 OK, now issue a RNTO");
                    } else if (cmd.equals("RNTO") && mRnfr != null && arg != null) {
                        File fs = mFakeRoot.getFile(mRnfr);
                        mRnfr = null;
                        File fd = mFakeRoot.getFile(arg);
                        if (fs == null || fd == null) {
                            send("553 Could not rename file. Probably wrong name(s)");
                        } else {
                            if (fs.renameTo(fd)) {
                                send("250 File renamed successfully");
                            } else {
                                send("553 Unable to rename file");
                            }
                        }
                    } else if (cmd.equals("REST") && arg != null) {
                        try {
                            mRest = Long.parseLong(arg);
                            send("350 Restarting at position " + mRest + ", now issue STOR or RETR!");
                        } catch (Exception e) {
                            send("554 Invalid REST parameter");
                        }
                    } else if (cmd.equals("RETR") && arg != null) {
                        retr(arg);
                    } else if (cmd.equals("APPE") && arg != null) {
                        mRest = -1;
                        stor(arg);
                    } else if (cmd.equals("STOR") && arg != null) {
                        stor(arg);
                    } else if (cmd.equals("TYPE") && arg != null && arg.equals("I")) {
                        transferMode = TransferMode.BINARY;
                        send("200 Type set to I");
                    } else if (cmd.equals("TYPE") && arg != null && arg.equals("A")) {
                        transferMode = TransferMode.TEXT;
                        send("200 Type set to A");
                    } else if (cmd.equals("PWD") || cmd.equals("XPWD")) {
                        send("257 \"" + mFakeRoot.getCurDir() + "\" is current directory.");
                    } else if (cmd.equals("RETR")) {
                        send("150");
                    } else {
                        send("500 " + cmd + " not understood");
                    }
                }
            }
        } catch (SocketException e) {
            if (!clientQuit) {
                logger.error(e);
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            if (mServerSocketData != null) {
                try {
                    mServerSocketData.close();
                    if (mSocket != null && mSocket.isConnected() && !mSocket.isClosed()) {
                        send("421 Timeout.");
                    }
                } catch (Exception e) {
                    logger.error(e);
                }
            }
            // Close connection to
            if (mSocket != null) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                }
            }
            logger.notify("Connection closed to " + (loggedIn ? username + "@ " : "") + mSocket.getInetAddress());
        }
    }

    /**
     * @param cmd
     * @param arg
     * @return true if command was handled, false otherwise
     * @throws IOException
     */
    private boolean handleCommand(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "QUIT":
                send("221 Goodbye.");
                clientQuit = true;
                mSocket.close();
                break;
            case "FEAT":
                send("211-Features:");
                send(" SIZE");
                send(" MDTM");
                send(" PASV");
                send(" REST");
                send(" UTF8");
                send("211 End");
                break;
            case "SYST":
                send("215 UNIX Type: L8");
                break;
            case "INFOSYS":
                send(mMyFTP.getSysInfo());
                break;
            case "OPTS":
                if (arg != null && arg.equalsIgnoreCase("UTF8 ON")) {
                    mCurrentEncoding = "UTF8";
                    send("200 yeah sure");
                }
                break;
            case "NOOP":
                send("200 noop ok, although i'd rather see you do something useful :-)");
                break;
            case "USER":
                send("331 Ok, password please");
                if (arg != null) {
                    username = arg;
                }
                break;
            case "PASS":
                Account a;
                if (arg != null) {
                    a = Account.getAccount(username, arg);
                } else {
                    a = null;
                }

                if (a == null) {
                    logger.notify("LoginFail for " + username);
                    send("530 Login incorrect.");
                } else {
                    logger.notify("Login " + username);
                    loggedIn = true;
                    logger.setUser(a.getLogin());
                    if (a.getHomeDir() != null) {
                        mFakeRoot = new FakeRoot(a.getHomeDir());
                    }
                    send("230 User " + a.getName() + " logged in");
                    mAccount = a;
                }
                break;
            default:
                return false;
        }
        return true;
    }
}
