FROM --platform=linux/amd64 ubuntu:20.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3 build-essentials checkinstall

RUN wget http://www.cmake.org/files/v3.20/cmake-3.20.0.tar.gz
RUN tar -xvzf cmake-3.20.0.tar.gz 
RUN cd cmake-3.20.0/
RUN ./configure 
RUN make
RUN checkinstall
RUN cmake --version
RUN ..

RUN wget https://github.com/graalvm/labs-openjdk-17/releases/download/jvmci-22.3-b06/labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz && \
    tar -xzf labsjdk-ce-17.0.5+5-jvmci-22.3-b06-linux-amd64.tar.gz
ENV JAVA_HOME=/data/labsjdk-ce-17.0.5-jvmci-22.3-b06/

RUN git clone https://github.com/graalvm/mx.git && \
    cd mx; git checkout b62c4ec0; cd ..;
ENV PATH=/data/mx:$JAVA_HOME/bin:$PATH
RUN echo $PATH
RUN java -version && javac -version

RUN wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.2.0/graalvm-ce-java17-linux-amd64-22.2.0.tar.gz && \
    tar -xzf graalvm-ce-java17-linux-amd64-22.2.0.tar.gz
ENV PATH=/data/graalvm-ce-java17-22.2.0/bin:$PATH
RUN gu install native-image
RUN DEBIAN_FRONTEND="noninteractive" apt-get -y install build-essential libz-dev zlib1g-dev pip cmake gcc g++
RUN wget https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz && \
    tar -xzf apache-maven-3.9.11-bin.tar.gz
ENV PATH=/data/apache-maven-3.9.11/bin:$PATH

