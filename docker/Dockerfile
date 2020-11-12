FROM openjdk:8-jre-alpine

COPY files /ftp/

EXPOSE 21 2190-2199
VOLUME /ftp/files
WORKDIR /ftp
ENTRYPOINT [ "/bin/sh", "startFTP.sh" ]
