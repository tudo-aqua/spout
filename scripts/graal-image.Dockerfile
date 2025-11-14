FROM --platform=linux/amd64 ubuntu:20.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3

RUN wget https://github.com/graalvm/labs-openjdk-17/releases/download/jvmci-22.3-b06/labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz && \
    tar -xzf labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz
ENV JAVA_HOME=/data/labsjdk-ce-17.0.5-jvmci-22.3-b06/

RUN git clone https://github.com/graalvm/mx.git
ENV PATH=/data/mx:$JAVA_HOME/bin:$PATH
RUN echo $PATH
RUN java -version && javac -version

RUN wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-linux-amd64-22.2.0.tar.gz && \
    tar -xzf graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
ENV PATH=/data/graalvm-ce-java17-22.2.0/bin:$PATH
RUN gu install native-image
RUN DEBIAN_FRONTEND="noninteractive" apt-get -y install build-essential libz-dev zlib1g-dev pip cmake gcc g++
RUN wget https://dlcdn.apache.org/maven/maven-3/3.8.6/binaries/apache-maven-3.8.6-bin.tar.gz && \
    tar -xzf apache-maven-3.8.6-bin.tar.gz
ENV PATH=/data/apache-maven-3.8.6/bin:$PATH
