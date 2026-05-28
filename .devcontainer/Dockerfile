
# Dockerfile for RTranslator Codespace
FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Install dependencies
RUN apt-get update && \
    apt-get install -y wget unzip openjdk-17-jdk git && \
    rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Install Android SDK Command-line tools
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

FROM mcr.microsoft.com/devcontainers/base:ubuntu

RUN apt-get update && apt-get install -y wget unzip

RUN mkdir -p /home/vscode/android-sdk && cd /home/vscode/android-sdk && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-11076708_latest.zip -d cmdline-tools && \
    echo 'export ANDROID_HOME=/home/vscode/android-sdk' >> /home/vscode/.bashrc && \
    echo 'export PATH=$ANDROID_HOME/cmdline-tools/bin:$ANDROID_HOME/platform-tools:$PATH' >> /home/vscode/.bashrc
