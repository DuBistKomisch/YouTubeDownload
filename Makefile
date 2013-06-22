JC = javac
SRC = src
BIN = bin
LIB = lib
CP = -cp ${BIN}:${LIB}:${LIB}/*
JCARGS = -g ${CP}

SOURCES = ${wildcard ${SRC}/*.java}
CLASSES = ${patsubst ${SRC}/%.java,${BIN}/%.class,${SOURCES}}

# compile all the files
all: ${CLASSES}

# dependencies
${BIN}/QuickDownload.class: ${BIN}/Downloader.class
${BIN}/FeedDownload.class: ${BIN}/Downloader.class

# how to make a .java into a .class
${CLASSES}: ${BIN}/%.class:${SRC}/%.java
	@mkdir -p ${BIN}
	${JC} ${JCARGS} -d ${BIN} ${SRC}/$*.java

# delete compiled files
clean:
	rm -rf ${BIN}
