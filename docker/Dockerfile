FROM ubuntu:20.04

ENV DEBIAN_FRONTEND noninteractive

ARG SOUFFLE_GIT
ARG SOUFFLE_HASH




# RUN useradd -m javadl && echo javadl:javadl | chpasswd && \
# 	usermod -aG sudo javadl

# General-purpose utilities
RUN apt-get update && apt-get -y install \
        bash \
        git \
        gdb \
        gcc \
        emacs \
        vim \
        nano \
        sudo \
        autoconf \
        automake \
        bison \
        build-essential \
        doxygen \
        flex \
        g++ \
        git \
        libffi-dev \
        libncurses5-dev \
        libtool \
        libsqlite3-dev \
        make \
        mcpp \
        python \
        sqlite \
        zlib1g-dev \
        curl \
        ninja-build


# Locales (use UTF-8 instead of ASCII)
RUN apt-get install -y locales locales-all
ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

# Java; Java 8 is needed by Defects4J, Java 11 for everything else
RUN apt-get -y install openjdk-11-jdk

# Swig, needed for JavaDL to be able to interface Souffle-generated program
RUN apt-get -y install swig

# Python
RUN apt-get -y install python3
RUN apt-get -y install python3-pip

RUN mkdir -p /work/projects

# Haskell stack
RUN curl -sSL https://get.haskellstack.org/ | sh

# Build Souffle
RUN cd /work/projects && git clone $SOUFFLE_GIT souffle
RUN cd /work/projects/souffle/ && git checkout $SOUFFLE_HASH
RUN cd /work/projects/souffle && sh ./bootstrap && ./configure --enable-swig --enable-64bit-domain && make -j4
RUN echo "export PATH=$PATH:/work/projects/souffle/src/" >> ~/.bash_aliases

# Install cmake
RUN apt install -y cmake

# Build Clang libraries
ARG LLVM_GIT
ARG LLVM_HASH
RUN cd /work/projects && git clone $LLVM_GIT llvm-project && cd llvm-project && git checkout $LLVM_HASH
RUN mkdir /work/projects/llvm-project/build-release
RUN cd /work/projects/llvm-project/build-release && cmake -G Ninja -DCMAKE_BUILD_TYPE=Release \
        -DLLVM_ENABLE_PROJECTS="clang;clang-tools-extra" \
        -DBUILD_SHARED_LIBS=ON  -DLLVM_APPEND_VC_REV=OFF \
        ../llvm

RUN cd /work/projects/llvm-project/build-release && ninja libclangClogSWIG.so
# Build clang tidy
RUN cd /work/projects/llvm-project/build-release && ninja clang-tidy

# Build JavaDL
RUN git config --global url."https://github.com/".insteadOf "git://github.com:"
RUN git config --global url."https://github.com/".insteadOf "git@github.com:"

ARG CLOG_GIT
ARG CLOG_HASH
RUN cd /work/projects && git clone $CLOG_GIT metadl
RUN cd /work/projects/metadl && git checkout $CLOG_HASH && git submodule update --init --recursive

RUN cd /work/projects/metadl && ./gradlew jar

# Get Juliet
ARG JULIET_GIT
ARG JULIET_HASH
RUN cd /work/projects && git clone $JULIET_GIT juliet && cd juliet && git checkout $JULIET_HASH

# Install bear
RUN apt install -y bear re2c tcl

# Get Magma
ARG MAGMA_GIT
ARG MAGMA_HASH

RUN apt install -y pkgconf
RUN cd /work/projects && git clone $MAGMA_GIT magma && cd magma && git checkout $MAGMA_HASH && ./prepare.sh

# Get the evaluation framework
ARG CLOG_EVAL_GIT
ARG CLOG_EVAL_HASH
RUN cd /work/projects && git clone $CLOG_EVAL_GIT  clog-eval && cd clog-eval && git checkout $CLOG_EVAL_HASH
RUN cd /work/projects/clog-eval/framework && stack build

# Install bc and jq
RUN apt install -y bc jq pkg-config libicu-dev

# Run the evaluation
# RUN cd /work/projects/clog-eval && ./run-all.sh
# RUN cd /work/projects/clog-eval && ./mrun-all.sh
