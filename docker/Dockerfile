FROM ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive

#分开安装，确保 locales 文件存在
RUN apt-get update && apt-get install -y locales

#生成 en_US.UTF-8 locale（修复错误）
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen

#安装你原来的其他软件包
RUN apt-get install -y \
    vim \
    curl \
    wget \
    git \
    unzip \
    build-essential \
    software-properties-common \
    ca-certificates \
    libssl-dev \
    libffi-dev \
    python3 \
    python3-pip \
    python3-dev \
    openjdk-8-jdk && \
    ln -sf /usr/bin/python3 /usr/bin/python && \
    rm -rf /var/lib/apt/lists/*

# 设置 UTF-8 环境变量
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
# 升级 pip
RUN pip3 install --no-cache-dir --upgrade pip

# 安装 Python 包
RUN pip3 install --no-cache-dir --timeout=60 --retries=5 requests cryptography sseclient-py

# 设置 JAVA 环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# 打印版本信息（可选）
RUN java -version && python --version && pip3 --version && locale

WORKDIR /workspace
