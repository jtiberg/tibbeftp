#!/bin/sh -e

# Official settings
if [ -z "$FTP_USER" ]; then
  FTP_USER="ftp"
fi
if [ -z "$FTP_PASS" ]; then
  FTP_PASS="ftp"
fi
if [ -z "$FTP_DATA_PORT_RANGE" ]; then
  FTP_DATA_PORT_RANGE="2190-2199"
fi

# Undocumented settings
if [ -z "$FTP_PATH" ]; then
  FTP_PATH="./files"
fi

if [ ! -e accounts.txt ];then
	echo "$FTP_USER $FTP_PASS $FTP_PATH" >> accounts.txt
fi
echo "--- accounts.txt ---"
cat accounts.txt
echo "--------------------"

exec java -jar TibbeFTP.jar . -port=21:$FTP_DATA_PORT_RANGE
