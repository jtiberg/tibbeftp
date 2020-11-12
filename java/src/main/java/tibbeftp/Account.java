package tibbeftp;

import java.io.*;
import java.util.StringTokenizer;

/**
 * @author jesper
 */
public class Account {
    final String mLogin;
    final File mHomeDir;

    public Account(String login, File homeDir) {
        mLogin = login;
        mHomeDir = homeDir;
    }

    /**
     * @return The login username
     */
    public String getLogin() {
        return mLogin;
    }

    /**
     * @return This users home directory
     */
    public File getHomeDir() {
        return mHomeDir;
    }

    public String getName() {
        return mLogin;
    }


    /**
     * Lookup the user and password in accounts.txt
     * TODO: support MD5 hashed passwords (salted of course)
     *
     * @return an Account object - if the credentials were ok
     */
    public static Account getAccount(final String providedUserName, final String providedPassword) {
        File fAccounts = new File(Main.getFtpHome(), "accounts.txt");
        if (!fAccounts.exists()) {
            System.err.println(fAccounts + " missing!");
            return null;
        }
        BufferedReader in = null;
        try {
            FileInputStream fin = new FileInputStream(fAccounts);
            in = new BufferedReader(new InputStreamReader(fin));
            String tmp;
            while ((tmp = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(tmp);
                // Ignore lines starting with #
                if (tmp.trim().startsWith("#") || tmp.trim().length() == 0) {
                    continue;
                }
                // Wrong in configuration
                if (st.countTokens() < 2) {
                    System.out.println("Wrong line in file: " + tmp);
                    continue;
                }

                // Get user, pass and homeDir from accounts.txt
                final String user = st.nextToken();
                final String pass = st.nextToken();
                final File homeDir;
                if (st.hasMoreTokens()) {
                    homeDir = new File(st.nextToken("\n").trim());
                } else {
                    homeDir = new File(Main.getFtpHome(), "home/" + user);
                }

                // Verify that a username and password was retrieved from the line
                boolean credentialsOk = providedUserName.equals(user) && providedPassword.equals(pass);
                if (credentialsOk) {
                    if (!homeDir.isDirectory() && !homeDir.mkdirs()) {
                        System.err.println(homeDir + " does not exist and could not be created!");
                    }
                    Account ac = new Account(user, homeDir);
                    return ac;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
