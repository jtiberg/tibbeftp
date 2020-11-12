# tibbeftp (FTP Server)
Hobby project from the 90s + some (minor) evolution over the years.

It was developed for being a super easy no-installation type of application. At a time when not everyone was using Linux and Java was on every machine :-)

Over the years it's been used in production in some places which is why I'm putting it on GitHub as LGPL.

# How to build
```bash
cd java
./gradlew clean build
```

Target file: **build/TibbeFTP.jar**

# Usage

### Run as jar
java -jar TibbeFTP.jar \<ftp-base\> |-disable-logging| |-port=CommandPort|:DataPortMin-DataPortMax|

\<ftp-base\> is a directory containing accounts.txt (that you create) logs stored by the application and user home directories (if not set in accounts.txt)


### Run in docker
Please see: https://hub.docker.com/repository/docker/jespertiberg/tibbeftp

### Format for accounts.txt
This is a file with 2 or 3 columns per row.
Each row defines one account
\<**login**\> \<**password**\> *|users home directory (optional)|*


### Environment variables
**FTP_IP** : public IP for passive mode (if not set it will be looked up automatically)
**PASV_PROMISCUOUS** : allow data connections from other IPs than the one from command session (passive mode transfers)
