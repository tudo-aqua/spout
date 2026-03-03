FROM --platform=linux/amd64 ubuntu:20.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3 build-essential checkinstall libssl-dev

RUN wget http://www.cmake.org/files/v3.20/cmake-3.20.0.tar.gz && tar -xvzf cmake-3.20.0.tar.gz 
RUN cd cmake-3.20.0 && ./configure && make && checkinstall -y && cmake --version

ADD update_common.py .
RUN chmod +x update_common.py
RUN git clone https://github.com/graalvm/mx.git && \
    cd mx; git checkout 7.54.5; cd .. && \
    ./update_common.py mx/common.json; ./update_common.py mx/jdk-binaries.json;
    
ENV PATH=/data/mx:/data/labsjdk-gdart-25+37-jvmci-b01/bin:$PATH
RUN yes| mx fetch-jdk --strip-contents-home --to . labsjdk-gdart
RUN echo $PATH
RUN java -version && javac -version && mx --version

RUN DEBIAN_FRONTEND="noninteractive" apt-get -y install build-essential libz-dev zlib1g-dev pip gcc g++ openjdk-17-jdk python3-venv
RUN python -m venv /data/envs/mx_env ; source /data/envs/mx_env/bin/activate ; pip install ninja_syntax

RUN wget https://dlcdn.apache.org/maven/maven-3/3.9.12/binaries/apache-maven-3.9.12-bin.tar.gz && \
    tar -xzf apache-maven-3.9.12-bin.tar.gz
ENV PATH=/data/apache-maven-3.9.12/bin:$PATH
