FROM ubuntu:20.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3 && \
    wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.2.0/graalvm-ce-java11-linux-amd64-21.2.0.tar.gz && \
    tar -xzf graalvm-ce-java11-linux-amd64-21.2.0.tar.gz
ENV PATH=/data/graalvm-ce-java11-21.2.0/bin/:$PATH
ENV JAVA_HOME=/data/graalvm-ce-java11-21.2.0/

RUN git clone https://github.com/graalvm/mx.git
ENV PATH=/data/mx:$PATH
RUN echo $PATH
RUN java -version && javac -version
RUN gu install native-image
RUN apt-get install build-essential libz-dev zlib1g-dev
