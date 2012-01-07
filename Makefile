# This is a simple Makefile for building bio-formats for the Debian
# package.

.PHONY : all clean install

CLASSPATH := /usr/share/java/checkstyle.jar:/usr/share/java/ant-contrib.jar
export CLASSPATH

INSTALL_DIRECTORY := debian/libbio-formats-java

all :
	ant \
		jar-autogen \
		jar-common \
		jar-formats \
		jar-jai \
		jar-loci-plugins \
		jar-lwf-stubs \
		jar-mdbtools \
		jar-metakit \
		jar-ome-xml \
		jar-poi-loci \
		jar-scifio

clean :
	ant clean

install :
	mkdir -p $(INSTALL_DIRECTORY)/usr/share/java
	cp artifacts/*.jar $(INSTALL_DIRECTORY)/usr/share/java

