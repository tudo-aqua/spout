FROM --platform=linux/amd64 ubuntu:26.04
WORKDIR /data
RUN apt-get update && apt-get install -y wget git python3 python-is-python3 build-essential checkinstall libssl-dev cmake software-properties-common
RUN add-apt-repository --yes ppa:deadsnakes/ppa && apt-get update && apt install -y python3.12 python3.12-venv
ADD update_common.py .
RUN chmod +x update_common.py
RUN git clone https://github.com/graalvm/mx.git && \
    cd mx; git checkout 7.54.5; cd .. && \
    ./update_common.py mx/common.json; ./update_common.py mx/jdk-binaries.json;

ENV PATH=/data/mx:/data/labsjdk-gdart-25+37-jvmci-b01/bin:$PATH
RUN yes| mx fetch-jdk --strip-contents-home --to . labsjdk-gdart
RUN echo $PATH
RUN java -version && javac -version && mx --version

RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN DEBIAN_FRONTEND="noninteractive" apt-get -y install build-essential libz-dev zlib1g-dev pip gcc g++ openjdk-17-jdk python3-venv  maven
RUN python3.12 -m venv /data/envs/mx_env ; source /data/envs/mx_env/bin/activate ; pip install ninja_syntax
