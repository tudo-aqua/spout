FROM ubuntu:20.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3

RUN wget https://github.com/graalvm/labs-openjdk-17/releases/download/jvmci-22.3-b06/labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz && \
    tar -xzf labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz
ENV JAVA_HOME=/data/labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64


RUN git clone https://github.com/graalvm/mx.git
ENV PATH=/data/mx:$PATH
RUN echo $PATH
RUN java -version && javac -version
RUN gu install native-image
RUN DEBIAN_FRONTEND="noninteractive" apt-get -y install build-essential libz-dev zlib1g-dev pip maven
